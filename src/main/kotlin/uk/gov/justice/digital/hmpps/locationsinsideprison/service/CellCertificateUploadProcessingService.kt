package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CellCertificateUploadApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * Asynchronously processes a stored cell certificate upload: applies the uploaded capacities/cell-marks/
 * sanitation to each cell (one transaction per row), identifies temporarily-inactive cells that should
 * stay on the certificate (INACTIVE_TEMP) and finally generates a new current cell certificate.
 */
@Service
class CellCertificateUploadProcessingService(
  private val cellCertificateUploadRepository: CellCertificateUploadRepository,
  private val cellCertificateUploadLocationRepository: CellCertificateUploadLocationRepository,
  private val cellLocationRepository: CellLocationRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val sharedLocationService: SharedLocationService,
  private val cellCertificateService: CellCertificateService,
  private val clock: Clock,
  transactionManager: PlatformTransactionManager,
) {
  // Explicit transaction boundaries (rather than @Transactional) because process() calls these helpers via
  // self-invocation, which would not be intercepted by the @Transactional proxy. Each row runs in its own
  // committed transaction so its outcome is durable even if a later row fails.
  private val newTransaction = TransactionTemplate(transactionManager)
  private val requiresNewTransaction = TransactionTemplate(transactionManager).apply {
    propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
  }

  fun process(uploadId: UUID) {
    val context = startProcessing(uploadId) ?: return

    context.pendingLocationIds.forEach { locationId ->
      try {
        requiresNewTransaction.executeWithoutResult {
          processRow(locationId, context.linkedTransactionId, context.requestedBy)
        }
      } catch (e: Exception) {
        // Backstop: the per-row transaction marks the row FAILED, but guard against the orchestrator aborting.
        log.error("Failed to process cell certificate upload row $locationId", e)
      }
    }

    newTransaction.executeWithoutResult {
      finish(uploadId, context.linkedTransactionId)
    }
  }

  private fun startProcessing(uploadId: UUID): ProcessingContext? = newTransaction.execute {
    val upload = cellCertificateUploadRepository.findById(uploadId).orElse(null)
    when {
      upload == null -> {
        log.warn("Cell certificate upload $uploadId not found, ignoring")
        null
      }
      upload.status == CellCertificateUploadStatus.FINISHED -> {
        log.info("Cell certificate upload $uploadId already FINISHED, ignoring duplicate message")
        null
      }
      else -> {
        if (upload.status == CellCertificateUploadStatus.PENDING) {
          upload.status = CellCertificateUploadStatus.STARTED
          upload.startTime = LocalDateTime.now(clock)
        }

        val linkedTransaction = sharedLocationService.createLinkedTransaction(
          prisonId = upload.prisonId,
          type = TransactionType.CAPACITY_CHANGE,
          detail = "Cell certificate upload ${upload.id}",
          transactionInvokedBy = upload.requestedBy,
        )

        ProcessingContext(
          pendingLocationIds = upload.locations
            .filter { it.status == CellCertificateUploadLocationStatus.PENDING }
            .map { it.id!! },
          linkedTransactionId = linkedTransaction.transactionId!!,
          requestedBy = upload.requestedBy,
        )
      }
    }
  }

  private fun processRow(locationId: UUID, linkedTransactionId: UUID, requestedBy: String) {
    val row = cellCertificateUploadLocationRepository.findById(locationId).orElse(null) ?: return
    val now = LocalDateTime.now(clock)

    try {
      val cell = cellLocationRepository.findOneByKey(row.locationKey)
      if (cell == null) {
        row.markSkipped("Location not found", now)
      } else if (cell.isPermanentlyDeactivated()) {
        row.markSkipped("Archived location", now)
      } else {
        val linkedTransaction = linkedTransactionRepository.findById(linkedTransactionId).orElseThrow()
        applyToCell(cell, row, requestedBy, now, linkedTransaction)
      }
    } catch (e: Exception) {
      log.warn("Failed to process upload row for ${row.locationKey}: ${e.message}")
      row.markFailed("Update failed: ${e.message}", now)
    }
    cellCertificateUploadLocationRepository.save(row)
  }

  private fun applyToCell(
    cell: Cell,
    row: CellCertificateUploadLocation,
    requestedBy: String,
    now: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldMaxCapacity = cell.getMaxCapacity()
    val oldWorkingCapacity = cell.getCurrentlyHeldWorkingCapacity()
    val oldCertifiedNormalAccommodation = cell.getCertifiedNormalAccommodation()
    val oldCellMark = cell.getDoorCellMark()
    val oldInCellSanitation = cell.getSanitationOfCell()

    val cna = row.certifiedNormalAccommodation ?: oldCertifiedNormalAccommodation ?: 0

    var changed = false

    if (oldMaxCapacity != row.maxCapacity || oldWorkingCapacity != row.workingCapacity || oldCertifiedNormalAccommodation != cna) {
      cell.setCapacity(
        maxCapacity = row.maxCapacity,
        workingCapacity = row.workingCapacity,
        certifiedNormalAccommodation = cna,
        userOrSystemInContext = requestedBy,
        amendedDate = now,
        linkedTransaction = linkedTransaction,
      )
      changed = true
    }

    if (row.cellMark != null && row.cellMark != oldCellMark) {
      cell.setCellDoorMark(row.cellMark!!, requestedBy, now, linkedTransaction)
      changed = true
    }

    if (row.inCellSanitation != null && row.inCellSanitation != oldInCellSanitation) {
      cell.setSanitationOfCell(row.inCellSanitation!!, requestedBy, now, linkedTransaction)
      changed = true
    }

    // Identify temporarily-inactive cells that still hold a working capacity as INACTIVE_TEMP so the
    // certificate keeps their certified working capacity; clear the flag when the certified W/C is 0.
    if (cell.isTemporarilyDeactivated()) {
      if (row.workingCapacity > 0 && !cell.isShortTermInactive()) {
        cell.markAsTemporarilyOffCellCert()
        changed = true
      } else if (row.workingCapacity == 0 && cell.isShortTermInactive()) {
        cell.removeTemporarilyOffCellCert()
        changed = true
      }
    }

    row.recordPreviousValues(
      previousMaxCapacity = oldMaxCapacity,
      previousWorkingCapacity = oldWorkingCapacity,
      previousCertifiedNormalAccommodation = oldCertifiedNormalAccommodation,
      previousCellMark = oldCellMark,
      previousInCellSanitation = oldInCellSanitation,
    )
    if (changed) {
      row.markProcessed(now)
    } else {
      row.markSkipped("No changes required", now)
    }
  }

  private fun finish(uploadId: UUID, linkedTransactionId: UUID) {
    val upload = cellCertificateUploadRepository.findById(uploadId).orElse(null) ?: return
    if (upload.status == CellCertificateUploadStatus.FINISHED) return

    upload.processedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.PROCESSED }
    upload.skippedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.SKIPPED }
    upload.failedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.FAILED }

    val now = LocalDateTime.now(clock)
    val approvalRequest = certificationApprovalRequestRepository.save(
      CellCertificateUploadApprovalRequest(
        prisonId = upload.prisonId,
        requestedBy = upload.requestedBy,
        requestedDate = upload.requestedDate,
        reasonForChange = upload.reasonForChange ?: "Cell certificate upload ${upload.id}",
      ),
    )
    val linkedTransaction = linkedTransactionRepository.findById(linkedTransactionId).orElse(null)
    approvalRequest.approve(approvedBy = upload.requestedBy, approvedDate = now, linkedTransaction = linkedTransaction!!, clock = clock)

    val cellCertificate = cellCertificateService.createCellCertificate(
      approvedBy = upload.requestedBy,
      approvedDate = now,
      approvalRequest = approvalRequest,
      signedOperationCapacity = signedOperationCapacityRepository.findByPrisonId(upload.prisonId)?.signedOperationCapacity ?: 0,
    )

    upload.cellCertificateId = cellCertificate.id
    upload.status = CellCertificateUploadStatus.FINISHED
    upload.endTime = now
    linkedTransaction.txEndTime = now

    log.info("Finished cell certificate upload ${upload.id}: processed=${upload.processedRecords}, skipped=${upload.skippedRecords}, failed=${upload.failedRecords}, certificate=${cellCertificate.id}")
  }

  data class ProcessingContext(
    val pendingLocationIds: List<UUID>,
    val linkedTransactionId: UUID,
    val requestedBy: String,
  )

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
