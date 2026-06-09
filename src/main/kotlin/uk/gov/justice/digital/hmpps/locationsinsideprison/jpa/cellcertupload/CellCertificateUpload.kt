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
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadLocationDto
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

  open var reasonForChange: String? = null,

  /** Set once the certificate has been generated from this upload (later step). */
  open var cellCertificateId: UUID? = null,

  @SortNatural
  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "cell_certificate_upload_id", nullable = false)
  open var locations: SortedSet<CellCertificateUploadLocation> = sortedSetOf(),
) {
  fun addLocation(location: CellCertificateUploadLocation) {
    locations.add(location)
  }

  override fun toString(): String = "CellCertificateUpload(id=$id, prisonId='$prisonId', status=$status, totalRecords=$totalRecords)"

  fun toDto(includeLocations: Boolean = false): CellCertificateUploadDto = CellCertificateUploadDto(
    id = id!!,
    prisonId = prisonId,
    status = status,
    totalRecords = totalRecords,
    processedRecords = processedRecords,
    skippedRecords = skippedRecords,
    failedRecords = failedRecords,
    requestedBy = requestedBy,
    requestedDate = requestedDate,
    startTime = startTime,
    endTime = endTime,
    cellCertificateId = cellCertificateId,
    reasonForChange = reasonForChange,
    locations = if (includeLocations) locations.map { it.toDto() } else null,
  )
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

  open var previousWorkingCapacity: Int? = null,

  open var previousCertifiedNormalAccommodation: Int? = null,

  open var previousCellMark: String? = null,

  open var previousInCellSanitation: Boolean? = null,

  open var message: String? = null,

  open var processedDate: LocalDateTime? = null,
) : Comparable<CellCertificateUploadLocation> {

  fun recordPreviousValues(
    previousMaxCapacity: Int?,
    previousWorkingCapacity: Int?,
    previousCertifiedNormalAccommodation: Int?,
    previousCellMark: String?,
    previousInCellSanitation: Boolean?,
  ) {
    this.previousMaxCapacity = previousMaxCapacity
    this.previousWorkingCapacity = previousWorkingCapacity
    this.previousCertifiedNormalAccommodation = previousCertifiedNormalAccommodation
    this.previousCellMark = previousCellMark
    this.previousInCellSanitation = previousInCellSanitation
  }

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
    previousWorkingCapacity = previousWorkingCapacity,
    previousCertifiedNormalAccommodation = previousCertifiedNormalAccommodation,
    previousCellMark = previousCellMark,
    previousInCellSanitation = previousInCellSanitation,
  )

  override fun toString(): String = "CellCertificateUploadLocation(locationKey='$locationKey', status=$status)"
}
