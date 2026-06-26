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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
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
import java.time.Duration
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
  private val prisonerSearchService: PrisonerSearchService,
  private val snsService: SnsService,
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

    val capacityChangedLocationIds = mutableListOf<UUID>()
    context.pendingLocationIds.forEach { locationId ->
      try {
        val changedId = requiresNewTransaction.execute {
          processRow(locationId, uploadId, context.linkedTransactionId, context.requestedBy)
        }
        if (changedId != null) capacityChangedLocationIds.add(changedId)
      } catch (e: Exception) {
        // Backstop: the per-row transaction marks the row FAILED, but guard against the orchestrator aborting.
        log.error("Failed to process cell certificate upload row $locationId", e)
      }
    }

    newTransaction.executeWithoutResult {
      finish(uploadId, context.linkedTransactionId)
    }

    // Capacity changes are committed - now raise LOCATION_AMENDED events for each changed location.
    publishCapacityAmendedEvents(capacityChangedLocationIds)
  }

  /**
   * Publishes a LOCATION_AMENDED domain event for every cell whose capacity changed, plus its parent
   * locations - mirroring the synchronous bulk capacity update. Called after all capacity writes are
   * committed. Publishes the domain event directly (no audit) because this runs on the SQS listener thread
   * which has no security context; the capacity changes themselves are already audited via LocationHistory.
   * Failures are logged rather than propagated so they cannot trigger SQS redelivery.
   */
  private fun publishCapacityAmendedEvents(capacityChangedLocationIds: List<UUID>) {
    if (capacityChangedLocationIds.isEmpty()) return
    val now = LocalDateTime.now(clock)

    val locationsToAmend: List<Pair<UUID, String>> = newTransaction.execute {
      val cells = capacityChangedLocationIds.mapNotNull { cellLocationRepository.findById(it).orElse(null) }
      (cells + cells.flatMap { it.getParentLocations() })
        .filter { !it.isDraft() }
        .distinctBy { it.id }
        .map { it.id!! to it.getKey() }
    } ?: emptyList()

    locationsToAmend.forEach { (id, key) ->
      try {
        snsService.publishDomainEvent(
          eventType = InternalLocationDomainEventType.LOCATION_AMENDED,
          description = "$key ${InternalLocationDomainEventType.LOCATION_AMENDED.description}",
          occurredAt = now,
          additionalInformation = AdditionalInformation(id = id, key = key, source = InformationSource.DPS),
        )
      } catch (e: Exception) {
        log.error("Failed to publish LOCATION_AMENDED event for $key", e)
      }
    }
  }

  private fun startProcessing(uploadId: UUID): ProcessingContext? = newTransaction.execute {
    // Pessimistic lock so concurrent consumers (the same SQS message redelivered to multiple pods while a
    // long upload is still in flight) serialise here: the first claims the upload (PENDING -> STARTED) and
    // commits; the others then read STARTED and skip, guaranteeing a single run and a single certificate.
    val upload = cellCertificateUploadRepository.findByIdForUpdate(uploadId)
    val now = LocalDateTime.now(clock)
    when {
      upload == null -> {
        log.warn("Cell certificate upload $uploadId not found, ignoring")
        null
      }
      upload.status == CellCertificateUploadStatus.FINISHED -> {
        log.info("Cell certificate upload $uploadId already FINISHED, ignoring duplicate message")
        null
      }
      upload.status == CellCertificateUploadStatus.STARTED && !isStaleClaim(upload, now) -> {
        log.info("Cell certificate upload $uploadId already being processed by another consumer, ignoring duplicate message")
        null
      }
      else -> {
        // PENDING, or a STARTED claim that has gone stale (the previous run crashed) - (re)claim it and
        // process whatever rows are still PENDING.
        if (upload.status == CellCertificateUploadStatus.STARTED) {
          log.warn("Cell certificate upload $uploadId STARTED at ${upload.startTime} looks stale, re-claiming")
        }
        upload.status = CellCertificateUploadStatus.STARTED
        upload.startTime = now

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

  /** @return the cell's location id when its capacity (max/working/CNA) changed, otherwise null. */
  private fun processRow(locationId: UUID, uploadId: UUID, linkedTransactionId: UUID, requestedBy: String): UUID? {
    val row = cellCertificateUploadLocationRepository.findById(locationId).orElse(null) ?: return null
    val now = LocalDateTime.now(clock)
    var capacityChangedLocationId: UUID? = null

    try {
      val cell = cellLocationRepository.findOneByKey(row.locationKey)
      if (cell == null) {
        row.markFailed(LOCATION_NOT_FOUND_MESSAGE, now)
      } else if (cell.isPermanentlyDeactivated()) {
        row.markSkipped("Archived location", now)
      } else {
        val linkedTransaction = linkedTransactionRepository.findById(linkedTransactionId).orElseThrow()
        if (applyToCell(cell, row, requestedBy, now, linkedTransaction)) {
          capacityChangedLocationId = cell.id
        }
      }
    } catch (e: Exception) {
      log.warn("Failed to process upload row for ${row.locationKey}: ${e.message}")
      row.markFailed("Update failed: ${e.message}", now)
    }
    cellCertificateUploadLocationRepository.save(row)
    incrementRunningCount(uploadId, row.status)
    return capacityChangedLocationId
  }

  /**
   * Bumps the matching running count on the upload master record so the "so far" processed/skipped/failed
   * totals are visible to a GET refresh while the upload is still being processed. Committed with the row's
   * own transaction; finish() later recomputes the authoritative totals from the location rows.
   */
  private fun incrementRunningCount(uploadId: UUID, status: CellCertificateUploadLocationStatus) {
    when (status) {
      CellCertificateUploadLocationStatus.PROCESSED -> cellCertificateUploadRepository.incrementProcessedRecords(uploadId)
      CellCertificateUploadLocationStatus.SKIPPED -> cellCertificateUploadRepository.incrementSkippedRecords(uploadId)
      CellCertificateUploadLocationStatus.FAILED -> cellCertificateUploadRepository.incrementFailedRecords(uploadId)
      CellCertificateUploadLocationStatus.PENDING -> {}
    }
  }

  /** @return true when the cell's capacity (max/working/CNA) values were changed. */
  private fun applyToCell(
    cell: Cell,
    row: CellCertificateUploadLocation,
    requestedBy: String,
    now: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ): Boolean {
    val oldMaxCapacity = cell.getMaxCapacity()
    val oldWorkingCapacity = cell.getCurrentlyHeldWorkingCapacity()
    val oldCertifiedNormalAccommodation = cell.getCertifiedNormalAccommodation()
    val oldCellMark = cell.getDoorCellMark()
    val oldInCellSanitation = cell.getSanitationOfCell()

    val cna = row.certifiedNormalAccommodation ?: oldCertifiedNormalAccommodation ?: 0

    var changed = false
    var capacityChanged = false

    if (oldMaxCapacity != row.maxCapacity || oldWorkingCapacity != row.workingCapacity || oldCertifiedNormalAccommodation != cna) {
      // Look up occupancy via the non-transactional search service directly: a failure here must mark just this
      // row FAILED (caught below), not roll back the per-row transaction the way a throwing @Transactional bean would.
      val occupancy = prisonerSearchService.findPrisonersInLocations(cell.prisonId, listOf(cell.getPathHierarchy())).size
      validateCapacityNotBelowOccupancy(cell, occupancy, row.maxCapacity, row.workingCapacity)
      cell.setCapacity(
        maxCapacity = row.maxCapacity,
        workingCapacity = row.workingCapacity,
        certifiedNormalAccommodation = cna,
        userOrSystemInContext = requestedBy,
        amendedDate = now,
        linkedTransaction = linkedTransaction,
      )
      changed = true
      capacityChanged = true
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
    return capacityChanged
  }

  private fun finish(uploadId: UUID, linkedTransactionId: UUID) {
    // Lock the row again so the FINISHED check-and-set is atomic - defence in depth against any concurrent
    // path slipping past the claim guard and double-creating a certificate.
    val upload = cellCertificateUploadRepository.findByIdForUpdate(uploadId) ?: return
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
        reasonForChange = UPLOAD_REASON_FOR_CHANGE,
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

  /**
   * A STARTED claim is considered stale (its consumer crashed) once its startTime is older than
   * [STALE_CLAIM_THRESHOLD], allowing a redelivered message to re-claim and finish the upload.
   */
  private fun isStaleClaim(upload: CellCertificateUpload, now: LocalDateTime): Boolean {
    val startTime = upload.startTime ?: return true
    return startTime.isBefore(now.minus(STALE_CLAIM_THRESHOLD))
  }

  data class ProcessingContext(
    val pendingLocationIds: List<UUID>,
    val linkedTransactionId: UUID,
    val requestedBy: String,
  )

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    /** How long a STARTED upload can sit untouched before a redelivery is allowed to re-claim it. */
    private val STALE_CLAIM_THRESHOLD: Duration = Duration.ofMinutes(30)

    /** Failure message shown when an uploaded cell certificate row references a location we do not hold. */
    const val LOCATION_NOT_FOUND_MESSAGE = "Location not found on Residential locations"

    /** Fixed explanation recorded against the approval request generated by a cell certificate upload. */
    const val UPLOAD_REASON_FOR_CHANGE =
      "This is the cell certificate that was imported when the prison started using Residential locations to manage its cell certificate."
  }
}
