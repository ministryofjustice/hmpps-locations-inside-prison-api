package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

/**
 * Request format to upsert a location
 */
@Schema(description = "Request to upsert a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpsertLocationRequest(

  @Schema(description = "Location UUID, provided if a new location", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = false)
  val id: UUID? = null,

  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  override val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val localName: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  override val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  override val deactivationReason: DeactivatedReason? = null,

  @Schema(description = "Proposed re-activation date", example = "2025-01-05", required = false)
  override val proposedReactivationDate: LocalDate? = null,

  @Schema(description = "Date deactivation occurred", example = "2023-01-05", required = false)
  override val deactivatedDate: LocalDate? = null,

  @Schema(description = "Path hierarchy of the parent (if one exists)", example = "A-1", required = false)
  val parentLocationPath: String? = null,

  @Schema(description = "Parent UUID of the parent location (if one exists)", example = "2475f250-434a-4257-afe7-b911f1773a4e", required = false)
  val parentId: UUID? = null,

  @Schema(description = "Capacity details of the location", required = false)
  override val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  override val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  override val attributes: Set<ResidentialAttributeValue>? = null,

  @Schema(description = "Location Usage", required = false)
  override val usage: Set<NonResidentialUsageDto>? = null,

  @Schema(description = "Date location was created, if not provided then the current time will be used for a new location", required = false)
  val createDate: LocalDateTime? = null,

  @Schema(description = "Last updated, if not provided then the current time will be used", required = false)
  val lastModifiedDate: LocalDateTime? = null,

  @Schema(description = "Username of the staff updating the location", required = true)
  val lastUpdatedBy: String,

) : UpdateLocationRequest {

  private fun isCell() = locationType == LocationType.CELL

  fun toNewEntity(clock: Clock): LocationJPA {
    val now = LocalDateTime.now(clock)
    val location = if (residentialHousingType != null) {
      if (isCell()) {
        val location = Cell(
          id = null,
          prisonId = prisonId,
          code = code,
          locationType = locationType,
          pathHierarchy = code,
          active = !isDeactivated(),
          localName = localName,
          residentialHousingType = residentialHousingType,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: now,
          deactivatedDate = null,
          deactivatedReason = null,
          proposedReactivationDate = null,
          childLocations = mutableListOf(),
          parent = null,
          capacity = capacity?.let {
            uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity(
              maxCapacity = it.maxCapacity,
              workingCapacity = it.workingCapacity,
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
        ResidentialLocation(
          prisonId = prisonId,
          code = code,
          locationType = locationType,
          pathHierarchy = code,
          active = !isDeactivated(),
          localName = localName,
          residentialHousingType = residentialHousingType,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: now,
          childLocations = mutableListOf(),
        )
      }
    } else {
      val location = NonResidentialLocation(
        prisonId = prisonId,
        code = code,
        locationType = locationType,
        pathHierarchy = code,
        active = !isDeactivated(),
        localName = localName,
        comments = comments,
        orderWithinParentLocation = orderWithinParentLocation,
        createdBy = lastUpdatedBy,
        whenCreated = createDate ?: now,
        childLocations = mutableListOf(),
      )
      usage?.forEach { usage ->
        location.addUsage(usage.usageType, usage.capacity, usage.sequence)
      }
      location
    }

    if (isDeactivated()) {
      location.deactivatedReason = deactivationReason
      location.deactivatedDate = deactivatedDate
      location.proposedReactivationDate = proposedReactivationDate
    }
    return location
  }
}
