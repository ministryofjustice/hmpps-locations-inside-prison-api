package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

/**
 * Request format to upsert a location
 */
@Schema(description = "Request to upsert a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpsertLocationRequest(

  @Schema(description = "Location Id, provided if a new location", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = false)
  val id: UUID? = null,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 40, message = "Code must be less than 41 characters")
  override val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  override val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  override val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Path hierarchy of the parent (if one exists)", example = "A-1", required = false)
  val parentLocationPath: String? = null,

  @Schema(description = "Capacity details of the location", required = false)
  override val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  override val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  override val attributes: Set<ResidentialAttributeValue>? = null,

  @Schema(description = "Location Usage", required = false)
  override val usage: Set<NonResidentialUsageDto>? = null,

  @Schema(description = "Location Usage", required = false)
  val lastModifiedDate: LocalDateTime,

  @Schema(description = "Location Usage", required = false)
  val lastUpdatedBy: String,

) : UpdateLocationRequest {

  fun toNewEntity(clock: Clock): LocationJPA =
    if (residentialHousingType != null) {
      val location = ResidentialLocation(
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
        updatedBy = lastUpdatedBy,
        whenCreated = LocalDateTime.now(clock),
        whenUpdated = LocalDateTime.now(clock),
        deactivatedDate = null,
        deactivatedReason = null,
        reactivatedDate = null,
        childLocations = mutableListOf(),
        parent = null,
        capacity = capacity?.let {
          uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity(
            capacity = it.capacity,
            operationalCapacity = it.operationalCapacity,
          )
        },
        certification = certification?.let {
          uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification(
            certified = it.certified,
            capacityOfCertifiedCell = it.capacityOfCertifiedCell,
          )
        },
      )
      attributes?.forEach { attribute ->
        location.addAttribute(attribute)
      }
      location
    } else {
      val location = NonResidentialLocation(
        id = null,
        prisonId = prisonId,
        code = code,
        locationType = locationType,
        pathHierarchy = code,
        description = description,
        comments = comments,
        orderWithinParentLocation = orderWithinParentLocation,
        active = true,
        updatedBy = lastUpdatedBy,
        whenCreated = LocalDateTime.now(clock),
        whenUpdated = LocalDateTime.now(clock),
        deactivatedDate = null,
        deactivatedReason = null,
        reactivatedDate = null,
        childLocations = mutableListOf(),
        parent = null,
      )
      usage?.forEach { usage ->
        location.addUsage(usage.usageType, usage.capacity, usage.sequence)
      }
      location
    }
}
