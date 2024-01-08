package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.util.*

@Schema(description = "Location Information")
data class LocationDetail(
  @Schema(description = "Location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
  val id: UUID,

  @Schema(description = "Location Name", example = "A-1-001", required = true)
  val name: String,
)

/**
 * Request format to close a non-association between two prisoners
 */
@Schema(description = "Request to create a location")
data class CreateLocationRequest(
  @Schema(description = "Name of the location", required = true, example = "A-1-001", minLength = 1)
  @field:Size(min = 1, message = "Name cannot be blank")
  val name: String,
)
