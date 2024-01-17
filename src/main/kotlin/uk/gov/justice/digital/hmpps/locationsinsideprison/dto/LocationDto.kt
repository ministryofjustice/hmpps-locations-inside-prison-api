package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

@Schema(description = "Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Location(
  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Location Code", example = "001", required = true)
  val code: String,

  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @Schema(description = "Top Level Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = true)
  val topLevelId: UUID,

  @Schema(description = "Child Locations", required = false)
  val childLocations: List<Location>? = null,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  val orderWithinParentLocation: Int? = null,

  @Schema(description = "Indicates the location is enabled", example = "true", required = true)
  val active: Boolean = true,

  @Schema(description = "Date the location was deactivated", example = "2023-01-23", required = false)
  var deactivatedDate: LocalDate? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  var deactivatedReason: DeactivatedReason? = null,

  @Schema(description = "Date the location was reactivated", example = "2023-01-24", required = false)
  var reactivatedDate: LocalDate? = null,

  @Schema(description = "Capacity of the location", example = "2", required = false)
  var capacity: Int? = null,
  @Schema(description = "Operational capacity of the location", example = "2", required = false)
  var operationalCapacity: Int? = null,
  @Schema(description = "Current occupancy number of this location", example = "1", required = false)
  var currentOccupancy: Int? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", example = "true", required = false)
  var certified: Boolean? = null,
  @Schema(description = "Indicates the capacity of the certified location (cell)", example = "1", required = false)
  var capacityOfCertifiedCell: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  var residentialHousingType: ResidentialHousingType? = null,

) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }
}

/**
 * Request format to create a location
 */
@Schema(description = "Request to create a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 3)
  @field:Size(min = 3, message = "PrisonId cannot be blank")
  @field:Size(max = 3, message = "PrisonId must be 3 characters")
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 40, message = "Code must be less than 41 characters")
  val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  val description: String? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  val orderWithinParentLocation: Int? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,
) {

  fun toNewEntity(createdBy: String, clock: Clock): LocationJPA {
    return LocationJPA(
      id = null,
      prisonId = prisonId,
      code = code,
      locationType = locationType,
      pathHierarchy = code,
      description = description,
      residentialHousingType = residentialHousingType,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = true,
      updatedBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      whenUpdated = LocalDateTime.now(clock),
    )
  }
}

/**
 * Request format to create a location
 */
@Schema(description = "Request to update a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchLocationRequest(

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 40, message = "Code must be less than 41 characters")
  val code: String? = null,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType? = null,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  val orderWithinParentLocation: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,
)

/**
 * Request format to create a location
 */
@Schema(description = "Request to deactivate a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeactivationLocationRequest(
  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = true)
  val deactivationReason: DeactivatedReason,
)

fun LocationJPA.updateWith(patch: PatchLocationRequest, updatedBy: String, clock: Clock): LocationJPA {
  setCode(patch.code ?: this.getCode())
  this.locationType = patch.locationType ?: this.locationType
  this.description = patch.description ?: this.description
  this.comments = patch.comments ?: this.comments
  this.orderWithinParentLocation = patch.orderWithinParentLocation ?: this.orderWithinParentLocation
  this.residentialHousingType = patch.residentialHousingType ?: this.residentialHousingType
  this.updatedBy = updatedBy
  this.whenUpdated = LocalDateTime.now(clock)

  return this
}
