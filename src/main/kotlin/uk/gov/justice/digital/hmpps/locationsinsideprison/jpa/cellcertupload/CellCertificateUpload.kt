package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateTotalsDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadOmittedLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertifiedCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

/**
 * Master record for a single cell certificate upload request.
 *
 * Captures the capacities/cell-marks/sanitation supplied by the client. The detail rows are stored
 * as PENDING and processed asynchronously by a background listener (added in a later step).
 */
@Entity
open class CellCertificateUpload(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  open val prisonId: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  open var status: CellCertificateUploadStatus = CellCertificateUploadStatus.PENDING,

  /** A preview works out what the import would do and then undoes it; an import changes the locations. */
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  open val mode: CellCertificateUploadMode = CellCertificateUploadMode.IMPORT,

  /** On an import, the preview it was continued from. */
  open val previewUploadId: UUID? = null,

  @Column(nullable = false)
  open val requestedBy: String,

  @Column(nullable = false)
  open val requestedDate: LocalDateTime,

  open var startTime: LocalDateTime? = null,

  open var endTime: LocalDateTime? = null,

  @Column(nullable = false)
  open var totalRecords: Int = 0,

  @Column(nullable = false)
  open var processedRecords: Int = 0,

  @Column(nullable = false)
  open var skippedRecords: Int = 0,

  @Column(nullable = false)
  open var failedRecords: Int = 0,

  /** Rows whose certified capacity differs from the capacity the location kept, needing review. */
  @Column(nullable = false)
  open var discrepancyRecords: Int = 0,

  /** Certifiable cells with no row in the upload, carried onto the new certificate at their current values. */
  @Column(nullable = false)
  open var notOnCertificateRecords: Int = 0,

  /** Of [notOnCertificateRecords], the cells already on the current certificate, carried forward unchanged. */
  @Column(nullable = false)
  open var carriedForwardRecords: Int = 0,

  open var reasonForChange: String? = null,

  /** Set once the certificate has been generated from this upload (later step). */
  open var cellCertificateId: UUID? = null,

  /**
   * The approval request this upload raised when it finished. Lets the cell certificate import request
   * details page find the ingestion behind it, which nothing else records.
   */
  open var certificationApprovalRequestId: UUID? = null,

  /** On a finished preview, the totals of the prison's current certificate, if it has one. */
  open var currentMaxCapacity: Int? = null,
  open var currentWorkingCapacity: Int? = null,
  open var currentCertifiedNormalAccommodation: Int? = null,

  /** On a finished preview, the totals the new certificate would have if the import went ahead. */
  open var projectedMaxCapacity: Int? = null,
  open var projectedWorkingCapacity: Int? = null,
  open var projectedCertifiedNormalAccommodation: Int? = null,

  @SortNatural
  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "cell_certificate_upload_id", nullable = false)
  open var locations: SortedSet<CellCertificateUploadLocation> = sortedSetOf(),

  /**
   * Certifiable cells that had no row in this upload, so were carried onto the new certificate at their
   * current values rather than the values the upload stated.
   */
  @SortNatural
  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "cell_certificate_upload_id", nullable = false)
  open var locationsNotOnCertificate: SortedSet<CellCertificateUploadOmittedLocation> = sortedSetOf(),
) {
  fun addLocation(location: CellCertificateUploadLocation) {
    locations.add(location)
  }

  fun isPreview() = mode == CellCertificateUploadMode.PREVIEW

  /**
   * A new PENDING import holding the same uploaded rows as this preview. Only the values the prison uploaded
   * are copied - never the preview's outcomes - because the import works everything out again against the
   * locations as they are when it runs.
   */
  fun copyAsImport(requestedBy: String, requestedDate: LocalDateTime): CellCertificateUpload = CellCertificateUpload(
    prisonId = prisonId,
    mode = CellCertificateUploadMode.IMPORT,
    previewUploadId = id,
    requestedBy = requestedBy,
    requestedDate = requestedDate,
    reasonForChange = reasonForChange,
    totalRecords = totalRecords,
  ).also { import ->
    locations.forEach { row ->
      import.addLocation(
        CellCertificateUploadLocation(
          locationKey = row.locationKey,
          maxCapacity = row.maxCapacity,
          workingCapacity = row.workingCapacity,
          certifiedNormalAccommodation = row.certifiedNormalAccommodation,
          cellMark = row.cellMark,
          inCellSanitation = row.inCellSanitation,
        ),
      )
    }
  }

  override fun toString(): String = "CellCertificateUpload(id=$id, prisonId='$prisonId', mode=$mode, status=$status, totalRecords=$totalRecords)"

  fun toDto(includeLocations: Boolean = false, continuedAsUploadId: UUID? = null): CellCertificateUploadDto = CellCertificateUploadDto(
    id = id!!,
    prisonId = prisonId,
    status = status,
    mode = mode,
    previewUploadId = previewUploadId,
    continuedAsUploadId = continuedAsUploadId,
    currentCertificateTotals = totalsOrNull(currentMaxCapacity, currentWorkingCapacity, currentCertifiedNormalAccommodation),
    projectedCertificateTotals = totalsOrNull(projectedMaxCapacity, projectedWorkingCapacity, projectedCertifiedNormalAccommodation),
    totalRecords = totalRecords,
    processedRecords = processedRecords,
    skippedRecords = skippedRecords,
    failedRecords = failedRecords,
    discrepancyRecords = discrepancyRecords,
    notOnCertificateRecords = notOnCertificateRecords,
    carriedForwardRecords = carriedForwardRecords,
    requestedBy = requestedBy,
    requestedDate = requestedDate,
    startTime = startTime,
    endTime = endTime,
    cellCertificateId = cellCertificateId,
    certificationApprovalRequestId = certificationApprovalRequestId,
    reasonForChange = reasonForChange,
    locations = if (includeLocations) locations.map { it.toDto() } else null,
    locationsNotOnCertificate = if (includeLocations) locationsNotOnCertificate.map { it.toDto() } else null,
  )

  private fun totalsOrNull(maxCapacity: Int?, workingCapacity: Int?, certifiedNormalAccommodation: Int?) = if (maxCapacity != null && workingCapacity != null && certifiedNormalAccommodation != null) {
    CellCertificateTotalsDto(maxCapacity, workingCapacity, certifiedNormalAccommodation)
  } else {
    null
  }
}

/**
 * Detail record for a single cell in an upload. Holds the requested values and (after processing,
 * in a later step) the previous values and the outcome of the change.
 */
@Entity
open class CellCertificateUploadLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  open val locationKey: String,

  @Column(nullable = false)
  open val maxCapacity: Int,

  @Column(nullable = false)
  open val workingCapacity: Int,

  open val certifiedNormalAccommodation: Int? = null,

  open val cellMark: String? = null,

  open val inCellSanitation: Boolean? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  open var status: CellCertificateUploadLocationStatus = CellCertificateUploadLocationStatus.PENDING,

  open var previousMaxCapacity: Int? = null,

  /**
   * The max capacity the location ended up with. It differs from the certified [maxCapacity] when the
   * location could not take the uploaded value, and when an uploaded zero had to be floored to one.
   */
  open var appliedMaxCapacity: Int? = null,

  open var previousWorkingCapacity: Int? = null,

  /**
   * The working capacity the location ended up with. It differs from the certified [workingCapacity] when the
   * location kept the value it already held, and from [previousWorkingCapacity] when a cell that held none
   * took the certified value.
   */
  open var appliedWorkingCapacity: Int? = null,

  open var previousCertifiedNormalAccommodation: Int? = null,

  open var previousCellMark: String? = null,

  open var previousInCellSanitation: Boolean? = null,

  /** The certificate holds the uploaded working capacity but the location kept its own. */
  @Column(nullable = false)
  open var workingCapacityMismatch: Boolean = false,

  /** The certificate holds the uploaded max capacity but the location kept its own. */
  @Column(nullable = false)
  open var maxCapacityMismatch: Boolean = false,

  /** The certificate holds the uploaded CNA but the location kept its own. */
  @Column(nullable = false)
  open var certifiedNormalAccommodationMismatch: Boolean = false,

  /**
   * What the prison's current certificate records for this cell when the upload was processed, so the report can
   * show where the new certificate differs. All null when the cell is not on the current certificate.
   */
  open var currentCertifiedMaxCapacity: Int? = null,
  open var currentCertifiedWorkingCapacity: Int? = null,
  open var currentCertifiedNormalAccommodation: Int? = null,

  open var message: String? = null,

  open var processedDate: LocalDateTime? = null,
) : Comparable<CellCertificateUploadLocation> {

  fun recordPreviousValues(
    previousMaxCapacity: Int?,
    previousWorkingCapacity: Int?,
    previousCertifiedNormalAccommodation: Int?,
    previousCellMark: String?,
    previousInCellSanitation: Boolean?,
    appliedMaxCapacity: Int?,
    appliedWorkingCapacity: Int?,
  ) {
    this.appliedMaxCapacity = appliedMaxCapacity
    this.appliedWorkingCapacity = appliedWorkingCapacity
    this.previousMaxCapacity = previousMaxCapacity
    this.previousWorkingCapacity = previousWorkingCapacity
    this.previousCertifiedNormalAccommodation = previousCertifiedNormalAccommodation
    this.previousCellMark = previousCellMark
    this.previousInCellSanitation = previousInCellSanitation
  }

  /**
   * Records that the uploaded certified capacity could not be (or must not be) applied to the location,
   * so the certificate and the location now hold different values for review.
   */
  fun recordDiscrepancy(
    workingCapacityMismatch: Boolean,
    maxCapacityMismatch: Boolean,
    certifiedNormalAccommodationMismatch: Boolean,
  ) {
    this.workingCapacityMismatch = workingCapacityMismatch
    this.maxCapacityMismatch = maxCapacityMismatch
    this.certifiedNormalAccommodationMismatch = certifiedNormalAccommodationMismatch
  }

  fun hasDiscrepancy() = workingCapacityMismatch || maxCapacityMismatch || certifiedNormalAccommodationMismatch

  fun markProcessed(processedDate: LocalDateTime) {
    this.status = CellCertificateUploadLocationStatus.PROCESSED
    this.processedDate = processedDate
  }

  fun markSkipped(message: String, processedDate: LocalDateTime) {
    this.status = CellCertificateUploadLocationStatus.SKIPPED
    this.message = message
    this.processedDate = processedDate
  }

  fun markFailed(message: String, processedDate: LocalDateTime) {
    this.status = CellCertificateUploadLocationStatus.FAILED
    this.message = message
    this.processedDate = processedDate
  }

  /**
   * Everything processing worked out for this row. A preview works a row out inside a transaction that is then
   * rolled back, so the outcome is copied out first and written back with [applyOutcome] afterwards.
   */
  fun recordCurrentCertified(certified: CertifiedCapacity?) {
    currentCertifiedMaxCapacity = certified?.maxCapacity
    currentCertifiedWorkingCapacity = certified?.workingCapacity
    currentCertifiedNormalAccommodation = certified?.certifiedNormalAccommodation
  }

  fun outcome() = CellCertificateUploadLocationOutcome(
    status = status,
    message = message,
    processedDate = processedDate,
    previousMaxCapacity = previousMaxCapacity,
    appliedMaxCapacity = appliedMaxCapacity,
    previousWorkingCapacity = previousWorkingCapacity,
    appliedWorkingCapacity = appliedWorkingCapacity,
    previousCertifiedNormalAccommodation = previousCertifiedNormalAccommodation,
    previousCellMark = previousCellMark,
    previousInCellSanitation = previousInCellSanitation,
    workingCapacityMismatch = workingCapacityMismatch,
    maxCapacityMismatch = maxCapacityMismatch,
    certifiedNormalAccommodationMismatch = certifiedNormalAccommodationMismatch,
    currentCertifiedMaxCapacity = currentCertifiedMaxCapacity,
    currentCertifiedWorkingCapacity = currentCertifiedWorkingCapacity,
    currentCertifiedNormalAccommodation = currentCertifiedNormalAccommodation,
  )

  fun applyOutcome(outcome: CellCertificateUploadLocationOutcome) {
    status = outcome.status
    message = outcome.message
    processedDate = outcome.processedDate
    previousMaxCapacity = outcome.previousMaxCapacity
    appliedMaxCapacity = outcome.appliedMaxCapacity
    previousWorkingCapacity = outcome.previousWorkingCapacity
    appliedWorkingCapacity = outcome.appliedWorkingCapacity
    previousCertifiedNormalAccommodation = outcome.previousCertifiedNormalAccommodation
    previousCellMark = outcome.previousCellMark
    previousInCellSanitation = outcome.previousInCellSanitation
    workingCapacityMismatch = outcome.workingCapacityMismatch
    maxCapacityMismatch = outcome.maxCapacityMismatch
    certifiedNormalAccommodationMismatch = outcome.certifiedNormalAccommodationMismatch
    currentCertifiedMaxCapacity = outcome.currentCertifiedMaxCapacity
    currentCertifiedWorkingCapacity = outcome.currentCertifiedWorkingCapacity
    currentCertifiedNormalAccommodation = outcome.currentCertifiedNormalAccommodation
  }

  companion object {
    private val COMPARATOR = compareBy<CellCertificateUploadLocation> { it.locationKey }
  }

  override fun compareTo(other: CellCertificateUploadLocation) = COMPARATOR.compare(this, other)

  fun toDto(): CellCertificateUploadLocationDto = CellCertificateUploadLocationDto(
    locationKey = locationKey,
    status = status,
    message = message,
    processedDate = processedDate,
    maxCapacity = maxCapacity,
    workingCapacity = workingCapacity,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
    cellMark = cellMark,
    inCellSanitation = inCellSanitation,
    previousMaxCapacity = previousMaxCapacity,
    appliedMaxCapacity = appliedMaxCapacity,
    previousWorkingCapacity = previousWorkingCapacity,
    appliedWorkingCapacity = appliedWorkingCapacity,
    previousCertifiedNormalAccommodation = previousCertifiedNormalAccommodation,
    previousCellMark = previousCellMark,
    previousInCellSanitation = previousInCellSanitation,
    workingCapacityMismatch = workingCapacityMismatch,
    maxCapacityMismatch = maxCapacityMismatch,
    certifiedNormalAccommodationMismatch = certifiedNormalAccommodationMismatch,
    currentCertifiedMaxCapacity = currentCertifiedMaxCapacity,
    currentCertifiedWorkingCapacity = currentCertifiedWorkingCapacity,
    currentCertifiedNormalAccommodation = currentCertifiedNormalAccommodation,
  )

  override fun toString(): String = "CellCertificateUploadLocation(locationKey='$locationKey', status=$status)"
}

/** The result of processing one uploaded row - see [CellCertificateUploadLocation.outcome]. */
data class CellCertificateUploadLocationOutcome(
  val status: CellCertificateUploadLocationStatus,
  val message: String?,
  val processedDate: LocalDateTime?,
  val previousMaxCapacity: Int?,
  val appliedMaxCapacity: Int?,
  val previousWorkingCapacity: Int?,
  val appliedWorkingCapacity: Int?,
  val previousCertifiedNormalAccommodation: Int?,
  val previousCellMark: String?,
  val previousInCellSanitation: Boolean?,
  val workingCapacityMismatch: Boolean,
  val maxCapacityMismatch: Boolean,
  val certifiedNormalAccommodationMismatch: Boolean,
  val currentCertifiedMaxCapacity: Int?,
  val currentCertifiedWorkingCapacity: Int?,
  val currentCertifiedNormalAccommodation: Int?,
)

/**
 * A certifiable cell that had no row in a cell certificate upload, recorded so the ingestion report can
 * disclose that it was still carried onto the new certificate at these current values.
 */
@Entity
@Table(name = "cell_certificate_upload_not_on_certificate")
open class CellCertificateUploadOmittedLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  open val locationId: UUID,

  @Column(nullable = false)
  open val locationKey: String,

  @Column(nullable = false)
  open val maxCapacity: Int,

  @Column(nullable = false)
  open val workingCapacity: Int,

  @Column(nullable = false)
  open val certifiedNormalAccommodation: Int,

  /** On the current certificate, so carried forward unchanged, rather than added to the certificate. */
  @Column(nullable = false)
  open val onCurrentCertificate: Boolean = false,
) : Comparable<CellCertificateUploadOmittedLocation> {

  companion object {
    private val COMPARATOR = compareBy<CellCertificateUploadOmittedLocation> { it.locationKey }
  }

  override fun compareTo(other: CellCertificateUploadOmittedLocation) = COMPARATOR.compare(this, other)

  fun toDto(): CellCertificateUploadOmittedLocationDto = CellCertificateUploadOmittedLocationDto(
    locationId = locationId,
    locationKey = locationKey,
    maxCapacity = maxCapacity,
    workingCapacity = workingCapacity,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
    onCurrentCertificate = onCurrentCertificate,
  )

  override fun toString(): String = "CellCertificateUploadOmittedLocation(locationKey='$locationKey')"
}
