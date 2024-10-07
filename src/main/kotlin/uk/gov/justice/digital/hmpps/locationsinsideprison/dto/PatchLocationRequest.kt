package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.util.*

interface PatchLocationRequest {
  val code: String?
  val parentId: UUID?
  val parentLocationKey: String?
}

/**
 * Request format to update a location
 */
@Schema(description = "Request to update a residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchResidentialLocationRequest(

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @Schema(description = "Key of parent location", example = "MDI-B-1", required = false)
  override val parentLocationKey: String? = null,

  @Schema(description = "Accommodation type", example = "NORMAL_ACCOMMODATION", required = false)
  val accommodationType: AccommodationType? = null,

  @Schema(description = "used For types", required = false)
  val usedFor: Set<UsedForType>? = null,
) : PatchLocationRequest

@Schema(description = "Request to update a non-res location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchNonResidentialLocationRequest(

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String? = null,

  @Schema(description = "Location Type", example = "APPOINTMENTS", required = true)
  val locationType: NonResidentialLocationType? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @Schema(description = "Key of parent location", example = "MDI-B-1", required = false)
  override val parentLocationKey: String? = null,

  @Schema(description = "Non-residential usage", required = false)
  val usage: Set<NonResidentialUsageDto>? = null,
) : PatchLocationRequest

@Schema(description = "Request to update the local name of a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateLocationLocalNameRequest(

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 30, message = "Description must be less than 31 characters")
  val localName: String? = null,

  @Schema(description = "Username of the staff updating the location", required = false)
  val updatedBy: String? = null,
)
