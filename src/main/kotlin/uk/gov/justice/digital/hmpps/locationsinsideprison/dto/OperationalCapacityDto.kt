package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Operational Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OperationalCapacityDto(

  @Schema(description = "Operational Capacity", example = "100", required = true)
  val capacity: Int,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Data Time stamp", example = "2024-11-11", required = true)
  val dateTime: LocalDateTime,

  @Schema(description = "Approved by", example = "MALEMAN", required = true)
  val approvedBy: String,

)
