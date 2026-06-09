package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Cell certificate upload summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCertificateUploadDto(
  @param:Schema(description = "Unique identifier for this upload, used to monitor progress", example = "01912e1e-0000-7000-8000-000000000000", required = true)
  val id: UUID,

  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Current status of the upload", example = "PENDING", required = true)
  val status: CellCertificateUploadStatus,

  @param:Schema(description = "Total number of records to be processed", example = "240", required = true)
  val totalRecords: Int,

  @param:Schema(description = "Number of records processed so far", example = "0", required = true)
  val processedRecords: Int,

  @param:Schema(description = "Number of records skipped", example = "0", required = true)
  val skippedRecords: Int,

  @param:Schema(description = "Number of records that failed", example = "0", required = true)
  val failedRecords: Int,

  @param:Schema(description = "Who requested the upload", example = "MALEXANDER_GEN", required = true)
  val requestedBy: String,

  @param:Schema(description = "When the upload was requested", required = true)
  val requestedDate: LocalDateTime,

  @param:Schema(description = "When processing started")
  val startTime: LocalDateTime? = null,

  @param:Schema(description = "When processing finished")
  val endTime: LocalDateTime? = null,

  @param:Schema(description = "ID of the cell certificate generated from this upload, set once complete")
  val cellCertificateId: UUID? = null,

  @param:Schema(description = "Reason supplied for the change, where required")
  val reasonForChange: String? = null,

  @param:Schema(description = "Per-cell results, only populated when drilling into a single upload")
  val locations: List<CellCertificateUploadLocationDto>? = null,
)

@Schema(description = "Result of processing a single uploaded cell")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCertificateUploadLocationDto(
  @param:Schema(description = "Cell location key", example = "MDI-A-1-001", required = true)
  val locationKey: String,

  @param:Schema(description = "Outcome of processing this cell", example = "PROCESSED", required = true)
  val status: CellCertificateUploadLocationStatus,

  @param:Schema(description = "Message describing the outcome, e.g. why it was skipped or failed")
  val message: String? = null,

  @param:Schema(description = "When this cell was processed")
  val processedDate: LocalDateTime? = null,

  @param:Schema(description = "Requested max capacity", example = "2")
  val maxCapacity: Int,

  @param:Schema(description = "Requested working capacity", example = "1")
  val workingCapacity: Int,

  @param:Schema(description = "Requested certified normal accommodation", example = "2")
  val certifiedNormalAccommodation: Int? = null,

  @param:Schema(description = "Requested cell mark (door number)", example = "A1-01")
  val cellMark: String? = null,

  @param:Schema(description = "Requested in-cell sanitation flag")
  val inCellSanitation: Boolean? = null,

  @param:Schema(description = "Max capacity before the change", example = "3")
  val previousMaxCapacity: Int? = null,

  @param:Schema(description = "Working capacity before the change", example = "2")
  val previousWorkingCapacity: Int? = null,

  @param:Schema(description = "Certified normal accommodation before the change", example = "2")
  val previousCertifiedNormalAccommodation: Int? = null,

  @param:Schema(description = "Cell mark before the change", example = "A1-99")
  val previousCellMark: String? = null,

  @param:Schema(description = "In-cell sanitation before the change")
  val previousInCellSanitation: Boolean? = null,
)
