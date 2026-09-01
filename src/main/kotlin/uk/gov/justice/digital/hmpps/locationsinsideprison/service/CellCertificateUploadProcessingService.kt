package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertifiedCapacity
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
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
        row.markSkipped(ARCHIVED_LOCATION_MESSAGE, now)
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
    if (row.hasDiscrepancy()) {
      cellCertificateUploadRepository.incrementDiscrepancyRecords(uploadId)
    }
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

    // An ingestion never moves the prison's working capacity: a difference between it and the uploaded
    // certified working capacity does not tell us which of the two is correct. The certificate records the
    // uploaded value, the location keeps its own, and the difference is reported for a user to resolve.
    val retainedWorkingCapacity = oldWorkingCapacity ?: 0
    val currentMaxCapacity = oldMaxCapacity ?: 0
    // A live location cannot hold a max capacity of zero (validateCapacity), but the certificate must record
    // what the prison uploaded - so floor only the value pushed onto the location, never the certified one.
    val locationMaxCapacity = row.maxCapacity.coerceAtLeast(1)
    val currentCertifiedNormalAccommodation = oldCertifiedNormalAccommodation ?: 0
    val requestedCna = row.certifiedNormalAccommodation ?: currentCertifiedNormalAccommodation

    var appliedMaxCapacity = currentMaxCapacity
    var appliedCertifiedNormalAccommodation = currentCertifiedNormalAccommodation
    var changed = false
    var capacityChanged = false

    if (locationMaxCapacity != currentMaxCapacity || requestedCna != currentCertifiedNormalAccommodation) {
      // Look up occupancy via the non-transactional search service directly: a failure here must mark just this
      // row FAILED (caught by the caller), not roll back the per-row transaction the way a throwing
      // @Transactional bean would.
      val occupancy = prisonerSearchService.findPrisonersInLocations(cell.prisonId, listOf(cell.getPathHierarchy())).size

      // Prefer everything the upload asked for, then degrade one value at a time, so a single value the cell
      // cannot take (max capacity below occupancy, a CNA of zero on normal accommodation) no longer discards
      // the rest of the row. setCapacity validates before it mutates, so a rejected attempt changes nothing.
      val candidates = listOf(
        locationMaxCapacity to requestedCna,
        currentMaxCapacity to requestedCna,
        locationMaxCapacity to currentCertifiedNormalAccommodation,
      ).distinct()
      for ((maxCapacity, cna) in candidates) {
        if (maxCapacity == currentMaxCapacity && cna == currentCertifiedNormalAccommodation) continue
        try {
          validateCapacityNotBelowOccupancy(cell, occupancy, maxCapacity, retainedWorkingCapacity)
          cell.setCapacity(
            maxCapacity = maxCapacity,
            workingCapacity = retainedWorkingCapacity,
            certifiedNormalAccommodation = cna,
            userOrSystemInContext = requestedBy,
            amendedDate = now,
            linkedTransaction = linkedTransaction,
          )
          appliedMaxCapacity = maxCapacity
          appliedCertifiedNormalAccommodation = cna
          changed = true
          capacityChanged = true
          break
        } catch (e: CapacityException) {
          log.info("${cell.getKey()}: cannot certify max capacity $maxCapacity / CNA $cna on the location: ${e.message}")
        }
      }
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
      appliedMaxCapacity = appliedMaxCapacity,
    )
    row.recordDiscrepancy(
      // A temporarily deactivated cell holds a working capacity of zero by definition, so comparing it with
      // the certified value says nothing - the INACTIVE_TEMP handling above already covers those cells.
      workingCapacityMismatch = !cell.isTemporarilyDeactivated() && row.workingCapacity != retainedWorkingCapacity,
      // Compared against the floored value: an uploaded max capacity of zero the location had to round up
      // to one is not something a user can resolve, so it must not be reported as a discrepancy.
      maxCapacityMismatch = locationMaxCapacity != appliedMaxCapacity,
      certifiedNormalAccommodationMismatch = requestedCna != appliedCertifiedNormalAccommodation,
    )

    if (changed) {
      row.markProcessed(now)
    } else {
      row.markSkipped(NO_CHANGES_REQUIRED_MESSAGE, now)
    }
    // A discrepancy is orthogonal to whether the location changed - a cell can keep every value it already
    // had and still be certified at a different working capacity - so it overwrites the outcome message.
    if (row.workingCapacityMismatch) {
      row.message = WORKING_CAPACITY_MISMATCH_MESSAGE
    } else if (row.hasDiscrepancy()) {
      row.message = CERTIFIED_CAPACITY_MISMATCH_MESSAGE
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
    upload.discrepancyRecords = upload.locations.count { it.hasDiscrepancy() }

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
      certifiedCapacityOverrides = certifiedCapacityOverrides(upload),
    )

    upload.cellCertificateId = cellCertificate.id
    upload.certificationApprovalRequestId = approvalRequest.id
    upload.status = CellCertificateUploadStatus.FINISHED
    upload.endTime = now
    linkedTransaction.txEndTime = now

    log.info("Finished cell certificate upload ${upload.id}: processed=${upload.processedRecords}, skipped=${upload.skippedRecords}, failed=${upload.failedRecords}, needingReview=${upload.discrepancyRecords}, certificate=${cellCertificate.id}")
  }

  /**
   * The certified capacity to record for each cell the upload covered, keyed by path hierarchy. These are the
   * values the uploaded certificate stated, which are not necessarily the values the locations ended up with -
   * the certificate must reflect the upload. Rows that could not be matched to a live cell (FAILED) contribute
   * nothing and those cells fall back to their current state; archived locations are left out of the
   * certificate altogether by [CellCertificateService.createCellCertificate].
   */
  private fun certifiedCapacityOverrides(upload: CellCertificateUpload): Map<String, CertifiedCapacity> = upload.locations
    .filter { it.status == CellCertificateUploadLocationStatus.PROCESSED || it.status == CellCertificateUploadLocationStatus.SKIPPED }
    .associate { row ->
      row.locationKey.removePrefix("${upload.prisonId}-") to CertifiedCapacity(
        maxCapacity = row.maxCapacity,
        workingCapacity = row.workingCapacity,
        certifiedNormalAccommodation = row.certifiedNormalAccommodation ?: row.previousCertifiedNormalAccommodation ?: 0,
      )
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

    /** Skip message for a row whose location has been permanently deactivated. */
    const val ARCHIVED_LOCATION_MESSAGE = "Archived location"

    /** Skip message for a row that asked for nothing the location did not already hold. */
    const val NO_CHANGES_REQUIRED_MESSAGE = "No changes required"

    /** Reported against a cell that kept its own working capacity while the certificate took the uploaded one. */
    const val WORKING_CAPACITY_MISMATCH_MESSAGE = "Working capacity and certified working capacity do not match"

    /** Reported when the max capacity or CNA on the certificate could not be applied to the location. */
    const val CERTIFIED_CAPACITY_MISMATCH_MESSAGE = "Certified capacity does not match the cell's capacity"

    /** Fixed explanation recorded against the approval request generated by a cell certificate upload. */
    const val UPLOAD_REASON_FOR_CHANGE =
      "This is the cell certificate that was imported when the prison started using Residential locations to manage its cell certificate."
  }
}
