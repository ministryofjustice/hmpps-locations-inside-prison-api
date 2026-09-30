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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadOmittedLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
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
 *
 * A preview runs exactly the same code, but every change it makes is rolled back. The import rules work a
 * row out by changing the cell - [applyToCell] tries each fallback with [Cell.setCapacity], which validates
 * and then applies - so there is no side-effect-free way to ask what a row would do. Running the real code
 * and undoing it means a preview can never disagree with the import. It gives the same answer row by row
 * because rows are independent: a capacity is stored only on its own cell, and wing and landing totals are
 * calculated rather than stored, so undoing one row cannot change the next row's outcome.
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
  private val cellCertificateRepository: CellCertificateRepository,
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

    if (context.preview) {
      processPreview(uploadId, context)
      return
    }
    val linkedTransactionId = context.linkedTransactionId!!

    val capacityChangedLocationIds = mutableListOf<UUID>()
    context.pendingLocationIds.forEach { locationId ->
      try {
        val changedId = requiresNewTransaction.execute {
          processRow(locationId, uploadId, linkedTransactionId, context.requestedBy, context.currentCertified)
        }
        if (changedId != null) capacityChangedLocationIds.add(changedId)
      } catch (e: Exception) {
        // Backstop: the per-row transaction marks the row FAILED, but guard against the orchestrator aborting.
        log.error("Failed to process cell certificate upload row $locationId", e)
      }
    }

    newTransaction.executeWithoutResult {
      finish(uploadId, linkedTransactionId)
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
    }

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

        // A preview changes nothing, so it records no linked transaction against the prison.
        val linkedTransaction = if (upload.isPreview()) {
          null
        } else {
          sharedLocationService.createLinkedTransaction(
            prisonId = upload.prisonId,
            type = TransactionType.CAPACITY_CHANGE,
            detail = "Cell certificate upload ${upload.id}",
            transactionInvokedBy = upload.requestedBy,
          )
        }

        ProcessingContext(
          pendingLocationIds = upload.locations
            .filter { it.status == CellCertificateUploadLocationStatus.PENDING }
            .map { it.id!! },
          linkedTransactionId = linkedTransaction?.transactionId,
          requestedBy = upload.requestedBy,
          prisonId = upload.prisonId,
          preview = upload.isPreview(),
          // read once for the whole run: every row records what the current certificate says for its cell
          currentCertified = certifiedCellCapacities(upload.prisonId).mapKeys { (path, _) -> "${upload.prisonId}-$path" },
        )
      }
    }
  }

  /** @return the cell's location id when its capacity (max/working/CNA) changed, otherwise null. */
  private fun processRow(
    locationId: UUID,
    uploadId: UUID,
    linkedTransactionId: UUID,
    requestedBy: String,
    currentCertified: Map<String, CertifiedCapacity>,
  ): UUID? {
    val row = cellCertificateUploadLocationRepository.findById(locationId).orElse(null) ?: return null
    row.recordCurrentCertified(currentCertified[row.locationKey])
    val capacityChangedLocationId = evaluateRow(row, requestedBy, LocalDateTime.now(clock)) {
      linkedTransactionRepository.findById(linkedTransactionId).orElseThrow()
    }
    recordRowResult(row, uploadId)
    return capacityChangedLocationId
  }

  /**
   * Works out and applies one row: fails it when the location cannot be found, skips it when the location is
   * archived, and otherwise applies it to the cell. Shared by imports and previews so both follow the same rules.
   * [flushChanges] makes a preview write its changes to the database before they are rolled back, so a change
   * the database would refuse fails the row just as it would for an import.
   *
   * @return the cell's location id when its capacity (max/working/CNA) changed, otherwise null.
   */
  private fun evaluateRow(
    row: CellCertificateUploadLocation,
    requestedBy: String,
    now: LocalDateTime,
    flushChanges: Boolean = false,
    linkedTransaction: () -> LinkedTransaction,
  ): UUID? {
    var capacityChangedLocationId: UUID? = null
    try {
      val cell = cellLocationRepository.findOneByKey(row.locationKey)
      if (cell == null) {
        row.markFailed(LOCATION_NOT_FOUND_MESSAGE, now)
      } else if (cell.isPermanentlyDeactivated()) {
        row.markSkipped(ARCHIVED_LOCATION_MESSAGE, now)
      } else {
        if (applyToCell(cell, row, requestedBy, now, linkedTransaction())) {
          capacityChangedLocationId = cell.id
        }
        if (flushChanges) cellLocationRepository.flush()
      }
    } catch (e: Exception) {
      log.warn("Failed to process upload row for ${row.locationKey}: ${e.message}")
      row.markFailed("Update failed: ${e.message}", now)
    }
    return capacityChangedLocationId
  }

  private fun recordRowResult(row: CellCertificateUploadLocation, uploadId: UUID) {
    cellCertificateUploadLocationRepository.save(row)
    incrementRunningCount(uploadId, row.status)
    if (row.hasDiscrepancy()) {
      cellCertificateUploadRepository.incrementDiscrepancyRecords(uploadId)
    }
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

    // An ingestion does not move a working capacity the prison already holds: a difference between it and
    // the uploaded certified working capacity does not tell us which of the two is correct. The certificate
    // records the uploaded value, the location keeps its own, and the difference is reported for a user to
    // resolve.
    val retainedWorkingCapacity = oldWorkingCapacity ?: 0
    // The exception is a cell that currently holds a working capacity of zero. Only today's value is checked,
    // not the cell's history, so a cell that once held a working capacity and was later set to zero is treated
    // the same as one that never had one. NOMIS never recorded one, so a prison migrating off it arrives with
    // zero on every cell and the uploaded certificate is the only source that has the real value - there is
    // nothing to weigh it against, so it wins. Temporarily deactivated cells are left alone:
    // markAsTemporarilyOffCellCert below is how they keep a certified working capacity.
    val requestedWorkingCapacity =
      if (!cell.isTemporarilyDeactivated() && retainedWorkingCapacity == 0 && row.workingCapacity > 0) {
        row.workingCapacity
      } else {
        retainedWorkingCapacity
      }
    val currentMaxCapacity = oldMaxCapacity ?: 0
    // A live location cannot hold a max capacity of zero (validateCapacity), but the certificate must record
    // what the prison uploaded - so floor only the value pushed onto the location, never the certified one.
    val locationMaxCapacity = row.maxCapacity.coerceAtLeast(1)
    val currentCertifiedNormalAccommodation = oldCertifiedNormalAccommodation ?: 0
    val requestedCna = row.certifiedNormalAccommodation ?: currentCertifiedNormalAccommodation

    var appliedMaxCapacity = currentMaxCapacity
    var appliedWorkingCapacity = retainedWorkingCapacity
    var appliedCertifiedNormalAccommodation = currentCertifiedNormalAccommodation
    var changed = false
    var capacityChanged = false

    if (locationMaxCapacity != currentMaxCapacity ||
      requestedCna != currentCertifiedNormalAccommodation ||
      requestedWorkingCapacity != retainedWorkingCapacity
    ) {
      // Look up occupancy via the non-transactional search service directly: a failure here must mark just this
      // row FAILED (caught by the caller), not roll back the per-row transaction the way a throwing
      // @Transactional bean would.
      val occupancy = prisonerSearchService.findPrisonersInLocations(cell.prisonId, listOf(cell.getPathHierarchy())).size

      // Prefer everything the upload asked for, then degrade one value at a time, so a single value the cell
      // cannot take (max capacity below occupancy, a CNA of zero on normal accommodation) no longer discards
      // the rest of the row. setCapacity validates before it mutates, so a rejected attempt changes nothing.
      // Where the working capacity is being retained the last three entries repeat the first three, so for
      // every cell that already holds one the ladder collapses back to the two-value form.
      val candidates = listOf(
        Triple(locationMaxCapacity, requestedWorkingCapacity, requestedCna),
        Triple(currentMaxCapacity, requestedWorkingCapacity, requestedCna),
        Triple(locationMaxCapacity, requestedWorkingCapacity, currentCertifiedNormalAccommodation),
        Triple(locationMaxCapacity, retainedWorkingCapacity, requestedCna),
        Triple(currentMaxCapacity, retainedWorkingCapacity, requestedCna),
        Triple(locationMaxCapacity, retainedWorkingCapacity, currentCertifiedNormalAccommodation),
      ).distinct()
      for ((maxCapacity, workingCapacity, cna) in candidates) {
        if (maxCapacity == currentMaxCapacity &&
          workingCapacity == retainedWorkingCapacity &&
          cna == currentCertifiedNormalAccommodation
        ) {
          continue
        }
        try {
          validateCapacityNotBelowOccupancy(cell, occupancy, maxCapacity, workingCapacity)
          cell.setCapacity(
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            certifiedNormalAccommodation = cna,
            userOrSystemInContext = requestedBy,
            amendedDate = now,
            linkedTransaction = linkedTransaction,
          )
          appliedMaxCapacity = maxCapacity
          appliedWorkingCapacity = workingCapacity
          appliedCertifiedNormalAccommodation = cna
          changed = true
          capacityChanged = true
          break
        } catch (e: CapacityException) {
          log.info("${cell.getKey()}: cannot certify max capacity $maxCapacity / working capacity $workingCapacity / CNA $cna on the location: ${e.message}")
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
      appliedWorkingCapacity = appliedWorkingCapacity,
    )
    row.recordDiscrepancy(
      // A temporarily deactivated cell keeps its stored working capacity but reports none while it is
      // inactive, so comparing the certified value against it says nothing - the INACTIVE_TEMP handling
      // above already covers those cells.
      workingCapacityMismatch = !cell.isTemporarilyDeactivated() && row.workingCapacity != appliedWorkingCapacity,
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
    } else if (appliedWorkingCapacity != retainedWorkingCapacity) {
      // The one case where the import moves a working capacity: say so, because the report otherwise looks
      // identical to a cell that simply matched.
      row.message = WORKING_CAPACITY_TAKEN_FROM_CERTIFICATE_MESSAGE
    }
    return capacityChanged
  }

  private fun finish(uploadId: UUID, linkedTransactionId: UUID) {
    // Lock the row again so the FINISHED check-and-set is atomic - defence in depth against any concurrent
    // path slipping past the claim guard and double-creating a certificate.
    val upload = cellCertificateUploadRepository.findByIdForUpdate(uploadId) ?: return
    if (upload.status == CellCertificateUploadStatus.FINISHED) return

    recordResults(upload)

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

    log.info("Finished cell certificate upload ${upload.id}: processed=${upload.processedRecords}, skipped=${upload.skippedRecords}, failed=${upload.failedRecords}, needingReview=${upload.discrepancyRecords}, notOnCertificate=${upload.notOnCertificateRecords}, certificate=${cellCertificate.id}")
  }

  /** The authoritative totals and the cells not on the upload, recorded as an upload or a preview finishes. */
  private fun recordResults(upload: CellCertificateUpload) {
    upload.processedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.PROCESSED }
    upload.skippedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.SKIPPED }
    upload.failedRecords = upload.locations.count { it.status == CellCertificateUploadLocationStatus.FAILED }
    upload.discrepancyRecords = upload.locations.count { it.hasDiscrepancy() }

    val omittedLocations = locationsNotOnCertificate(upload)
    // Mutate the managed collection in place - reassigning it trips Hibernate's orphan-removal check
    // ("no longer referenced by the owning entity instance") because it replaces its persistent wrapper.
    upload.locationsNotOnCertificate.clear()
    upload.locationsNotOnCertificate.addAll(omittedLocations)
    upload.notOnCertificateRecords = omittedLocations.size
    upload.carriedForwardRecords = omittedLocations.count { it.onCurrentCertificate }
  }

  /**
   * The certified capacity to record for each cell on the new certificate, keyed by path hierarchy.
   *
   * - A cell in the upload (PROCESSED or SKIPPED) takes the values the uploaded certificate stated, which are not
   *   necessarily the values the location ended up with - the certificate must reflect the upload. These always win.
   * - Any other certifiable cell on the prison's current certificate keeps the values it is certified at (MAPA-414):
   *   a file changes only what it lists, so leaving a cell out never re-certifies it. That includes a cell whose row
   *   FAILED, which was given no value.
   * - A cell on no certificate has no entry, so it falls back to the values Residential locations holds (a cell
   *   temporarily out of use therefore gets working capacity 0 unless a file says otherwise).
   *
   * Only cells are carried forward. The certificate also records wing and landing totals, and fixing those would
   * stop them being added up from their cells. Archived locations are left out of the certificate altogether by
   * [CellCertificateService.createCellCertificate]. Used by both the import and the preview, so they agree.
   */
  private fun certifiedCapacityOverrides(upload: CellCertificateUpload): Map<String, CertifiedCapacity> {
    val uploaded = upload.locations
      .filter { it.status == CellCertificateUploadLocationStatus.PROCESSED || it.status == CellCertificateUploadLocationStatus.SKIPPED }
      .associate { row ->
        row.locationKey.removePrefix("${upload.prisonId}-") to CertifiedCapacity(
          maxCapacity = row.maxCapacity,
          workingCapacity = row.workingCapacity,
          certifiedNormalAccommodation = row.certifiedNormalAccommodation ?: row.previousCertifiedNormalAccommodation ?: 0,
        )
      }
    return certifiedCellCapacities(upload.prisonId).filterKeys { it !in uploaded } + uploaded
  }

  /**
   * The certified values of every certifiable cell on the prison's current certificate, keyed by path hierarchy.
   * Empty when the prison has no current certificate.
   */
  private fun certifiedCellCapacities(prisonId: String): Map<String, CertifiedCapacity> {
    val certified = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(prisonId)?.certifiedCapacitiesByPath()
      ?: return emptyMap()
    val cellPaths = cellCertificateService.certifiableCells(prisonId).map { it.getPathHierarchy() }.toSet()
    return certified.filterKeys { it in cellPaths }
  }

  /**
   * Certifiable cells (same filter [CellCertificateService.createCellCertificate] applies) that were not
   * given a certified value by this upload. This list exists purely to disclose them to the user, with the values
   * they go onto the new certificate at: their certified values when they are on the current certificate
   * ([certifiedCapacityOverrides]), otherwise the values Residential locations holds. A cell whose row is FAILED
   * already has its own explanation as a failed row - the location could not be matched, or was matched but the
   * update itself failed - so it is excluded here regardless of which. A row can still be PENDING when this runs:
   * [processRow]'s own transaction is what marks a row FAILED, and process()'s outer catch around it only logs, so
   * a row whose commit itself failed is left PENDING with no certified value recorded anywhere - that cell must be
   * disclosed here too, rather than being silently treated as covered.
   */
  private fun locationsNotOnCertificate(upload: CellCertificateUpload): List<CellCertificateUploadOmittedLocation> {
    val coveredOrFailedPathHierarchies = upload.locations
      .filter { it.status != CellCertificateUploadLocationStatus.PENDING }
      .map { it.locationKey.removePrefix("${upload.prisonId}-") }
      .toSet()
    val carriedForward = certifiedCellCapacities(upload.prisonId)

    return cellCertificateService.certifiableCells(upload.prisonId)
      .filterNot { coveredOrFailedPathHierarchies.contains(it.getPathHierarchy()) }
      .sortedBy { it.getPathHierarchy() }
      .map { cell ->
        val certified = carriedForward[cell.getPathHierarchy()]
        CellCertificateUploadOmittedLocation(
          locationId = cell.id!!,
          locationKey = cell.getKey(),
          maxCapacity = certified?.maxCapacity ?: cell.calcMaxCapacity(),
          workingCapacity = certified?.workingCapacity ?: cell.calcWorkingCapacityForCertificate(),
          certifiedNormalAccommodation = certified?.certifiedNormalAccommodation ?: cell.calcCertifiedNormalAccommodation(),
          onCurrentCertificate = certified != null,
        )
      }
  }

  /**
   * Works out what a preview's import would do without changing anything: each row, and then the certificate,
   * is worked out by the import code inside a transaction that is rolled back. No domain events are published.
   */
  private fun processPreview(uploadId: UUID, context: ProcessingContext) {
    context.pendingLocationIds.forEach { rowId ->
      try {
        previewRow(rowId, uploadId, context.prisonId, context.requestedBy, context.currentCertified)
      } catch (e: Exception) {
        log.error("Failed to preview cell certificate upload row $rowId", e)
      }
    }
    finishPreview(uploadId)
  }

  /**
   * Works a row out as an import would - including any change to the cell and its history - then rolls all of it
   * back, keeping only the outcome, which is written to the row in a second transaction.
   */
  private fun previewRow(
    rowId: UUID,
    uploadId: UUID,
    prisonId: String,
    requestedBy: String,
    currentCertified: Map<String, CertifiedCapacity>,
  ) {
    val outcome = requiresNewTransaction.execute { status ->
      try {
        val row = cellCertificateUploadLocationRepository.findById(rowId).orElse(null) ?: return@execute null
        row.recordCurrentCertified(currentCertified[row.locationKey])
        evaluateRow(row, requestedBy, LocalDateTime.now(clock), flushChanges = true) {
          sharedLocationService.createLinkedTransaction(
            prisonId = prisonId,
            type = TransactionType.CAPACITY_CHANGE,
            detail = "Cell certificate preview $uploadId",
            transactionInvokedBy = requestedBy,
          )
        }
        row.outcome()
      } finally {
        status.setRollbackOnly()
      }
    } ?: return

    requiresNewTransaction.executeWithoutResult {
      val row = cellCertificateUploadLocationRepository.findById(rowId).orElse(null) ?: return@executeWithoutResult
      row.applyOutcome(outcome)
      recordRowResult(row, uploadId)
    }
  }

  /**
   * Finishes a preview. The new certificate is built as the import would build it and then rolled back, which
   * gives its totals without creating it; the cells not on the upload and the counts are recorded as for an import.
   */
  private fun finishPreview(uploadId: UUID) {
    val totals = try {
      newTransaction.execute { status ->
        try {
          projectCertificateTotals(uploadId)
        } finally {
          status.setRollbackOnly()
        }
      }
    } catch (e: Exception) {
      log.error("Failed to work out the certificate totals for cell certificate preview $uploadId", e)
      null
    }

    newTransaction.executeWithoutResult {
      val preview = cellCertificateUploadRepository.findByIdForUpdate(uploadId) ?: return@executeWithoutResult
      if (preview.status == CellCertificateUploadStatus.FINISHED) return@executeWithoutResult

      recordResults(preview)
      totals?.current?.let {
        preview.currentMaxCapacity = it.maxCapacity
        preview.currentWorkingCapacity = it.workingCapacity
        preview.currentCertifiedNormalAccommodation = it.certifiedNormalAccommodation
      }
      totals?.projected?.let {
        preview.projectedMaxCapacity = it.maxCapacity
        preview.projectedWorkingCapacity = it.workingCapacity
        preview.projectedCertifiedNormalAccommodation = it.certifiedNormalAccommodation
      }
      preview.status = CellCertificateUploadStatus.FINISHED
      preview.endTime = LocalDateTime.now(clock)

      log.info("Finished cell certificate preview ${preview.id}: willChange=${preview.processedRecords}, noChange=${preview.skippedRecords}, willFail=${preview.failedRecords}, needingReview=${preview.discrepancyRecords}, notOnCertificate=${preview.notOnCertificateRecords}")
    }
  }

  /**
   * Must run in a transaction that is rolled back: it raises and approves an approval request and creates the
   * certificate exactly as [finish] does, to read the new certificate's totals.
   */
  private fun projectCertificateTotals(uploadId: UUID): PreviewTotals? {
    val preview = cellCertificateUploadRepository.findById(uploadId).orElse(null) ?: return null
    val current = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(preview.prisonId)?.let {
      Totals(it.totalMaxCapacity, it.totalWorkingCapacity, it.totalCertifiedNormalAccommodation)
    }

    val now = LocalDateTime.now(clock)
    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      prisonId = preview.prisonId,
      type = TransactionType.CAPACITY_CHANGE,
      detail = "Cell certificate preview ${preview.id}",
      transactionInvokedBy = preview.requestedBy,
    )
    val approvalRequest = certificationApprovalRequestRepository.save(
      CellCertificateUploadApprovalRequest(
        prisonId = preview.prisonId,
        requestedBy = preview.requestedBy,
        requestedDate = preview.requestedDate,
        reasonForChange = UPLOAD_REASON_FOR_CHANGE,
      ),
    )
    approvalRequest.approve(approvedBy = preview.requestedBy, approvedDate = now, linkedTransaction = linkedTransaction, clock = clock)
    val certificate = cellCertificateService.createCellCertificate(
      approvedBy = preview.requestedBy,
      approvedDate = now,
      approvalRequest = approvalRequest,
      signedOperationCapacity = signedOperationCapacityRepository.findByPrisonId(preview.prisonId)?.signedOperationCapacity ?: 0,
      certifiedCapacityOverrides = certifiedCapacityOverrides(preview),
    )
    return PreviewTotals(
      current = current,
      projected = Totals(certificate.totalMaxCapacity, certificate.totalWorkingCapacity, certificate.totalCertifiedNormalAccommodation),
    )
  }

  data class Totals(val maxCapacity: Int, val workingCapacity: Int, val certifiedNormalAccommodation: Int)

  data class PreviewTotals(val current: Totals?, val projected: Totals)

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
    /** Null for a preview, which records no linked transaction. */
    val linkedTransactionId: UUID?,
    val requestedBy: String,
    val prisonId: String,
    val preview: Boolean,
    /** What the prison's current certificate records for each certifiable cell, keyed by location key. */
    val currentCertified: Map<String, CertifiedCapacity> = emptyMap(),
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

    /** Reported against a cell that held a working capacity of zero and so took the certified one. */
    const val WORKING_CAPACITY_TAKEN_FROM_CERTIFICATE_MESSAGE = "Working capacity changed to match certified working capacity"

    /** Reported when the max capacity or CNA on the certificate could not be applied to the location. */
    const val CERTIFIED_CAPACITY_MISMATCH_MESSAGE = "Certified capacity does not match the cell's capacity"

    /** Fixed explanation recorded against the approval request generated by a cell certificate upload. */
    const val UPLOAD_REASON_FOR_CHANGE =
      "This is the cell certificate that was imported when the prison started using Residential locations to manage its cell certificate."
  }
}
