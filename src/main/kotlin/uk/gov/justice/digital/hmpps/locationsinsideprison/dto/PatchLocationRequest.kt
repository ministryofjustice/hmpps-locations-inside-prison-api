package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.util.*

interface PatchLocationRequest {
  val code: String?
  val parentId: UUID?
  val parentLocationKey: String?
  val removeParent: Boolean?
  val localName: String?
  val comments: String?
}

/**
 * Request format to update a location
 */
@Schema(description = "Request to update a residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchResidentialLocationRequest(

  @param:Schema(description = "Code of the location", required = false, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String? = null,

  @param:Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @param:Schema(description = "Key of parent location", example = "MDI-B-1", required = false)
  @field:Size(max = 80, message = "Key must be less than 81 characters")
  override val parentLocationKey: String? = null,

  @param:Schema(description = "Indicates this location should move to the top of the hierarchy", example = "false", required = false)
  override val removeParent: Boolean? = false,

  @param:Schema(description = "Accommodation type", example = "NORMAL_ACCOMMODATION", required = false)
  val accommodationType: AccommodationType? = null,

  @param:Schema(description = "used For types", required = false)
  val usedFor: Set<UsedForType>? = null,

  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 30, message = "Description must be less than 31 characters")
  override val localName: String? = null,

  @param:Schema(description = "Location Type", example = "CELL", required = false)
  val locationType: ResidentialLocationType? = null,

  @param:Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  override val comments: String? = null,

  @param:Schema(description = "Cell mark of the location, this can only be used in Draft or a prison that does not require approval to change the certificate", required = false, example = "A1", minLength = 1)
  @field:Size(min = 1, message = "Mark cannot be blank")
  @field:Size(max = 12, message = "Mark can be up to 12 characters")
  val cellMark: String? = null,

  @param:Schema(description = "In-cell sanitation, this can only be used in Draft or a prison that does not require approval to change the certificate", example = "true", required = false)
  val inCellSanitation: Boolean? = null,

) : PatchLocationRequest

@Schema(description = "Request to update a non-res location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchNonResidentialLocationRequest(

  @param:Schema(description = "Code of the location", required = false, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String? = null,

  @param:Schema(description = "Location Type", example = "APPOINTMENTS", required = false)
  val locationType: NonResidentialLocationType? = null,

  @param:Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @param:Schema(description = "Key of parent location", example = "MDI-B-1", required = false)
  override val parentLocationKey: String? = null,

  @param:Schema(description = "Indicates this location should move to the top of the hierarchy", example = "false", required = false)
  override val removeParent: Boolean? = false,

  @param:Schema(description = "Services that use this location", required = false)
  val servicesUsingLocation: Set<ServiceType>? = null,

  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 30, message = "Description must be less than 31 characters")
  override val localName: String? = null,

  @param:Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  override val comments: String? = null,
) : PatchLocationRequest {
  fun toUsages() = servicesUsingLocation?.map { it.nonResidentialUsageType }?.toSet() ?: emptySet()
}

@Schema(description = "Request to update the local name of a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateLocationLocalNameRequest(

  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 30, message = "Description must be less than 31 characters")
  val localName: String? = null,

  @param:Schema(description = "Username of the staff updating the location", required = false)
  val updatedBy: String? = null,
)
