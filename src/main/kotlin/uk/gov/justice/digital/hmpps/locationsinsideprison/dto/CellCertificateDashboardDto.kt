package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "A summary row for a prison with a current cell certificate, used by the capacity management dashboard")
data class CellCertificateDashboardDto(
  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Prison name", example = "Moorland (HMP & YOI)", required = true)
  val prisonName: String,

  @param:Schema(description = "Certified working capacity for the prison", example = "1186", required = true)
  val certifiedWorkingCapacity: Int,

  @param:Schema(description = "Signed operational capacity for the prison", example = "1190", required = true)
  val signedOperationCapacity: Int,

  @param:Schema(description = "Number of pending certification change requests for the prison", example = "0", required = true)
  val pendingChangeRequests: Int,

  @param:Schema(description = "When the current certificate was last updated", example = "2025-02-02T12:00:00", required = true)
  val certificateLastUpdated: LocalDateTime,
)
