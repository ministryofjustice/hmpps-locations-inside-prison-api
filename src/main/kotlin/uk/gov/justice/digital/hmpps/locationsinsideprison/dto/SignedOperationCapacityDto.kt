package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Schema(description = "Signed Operation Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignedOperationCapacityDto(

  @Schema(description = "Signed Operation Capacity", example = "100", required = true)
  val signedOperationCapacity: Int,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Data Time stamp", example = "2024-11-11", required = true)
  val dateTime: LocalDateTime,

  @Schema(description = "Updated by", example = "MALEMAN", required = true)
  val updatedBy: String,

)

@Schema(description = "Request to create a Signed Operation Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignedOperationCapacityValidRequest(

  @Schema(description = "Signed Operation Capacity value", example = "100", required = true)
  val signedOperationCapacity: Int,

  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 3, pattern = "^[A-Z]{2}I|ZZZ$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 3, message = "Prison ID must be 3 characters like MDI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZZ$", message = "Prison ID must be 3 characters like MDI")
  val prisonId: String,

  @Schema(description = "Updated By", example = "USER", required = true)
  @field:Size(min = 1, max = 255, message = "USER")
  val updatedBy: String,
  )

