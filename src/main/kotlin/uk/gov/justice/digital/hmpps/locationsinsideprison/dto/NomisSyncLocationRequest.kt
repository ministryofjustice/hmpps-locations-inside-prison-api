package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType.HOLDING_CELL
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.getVirtualLocationCodes
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * Request format to sync a location from NOMIS
 */
@Schema(description = "Request to upsert/sync a location from NOMIS to the Locations API")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NomisSyncLocationRequest(

  @param:Schema(description = "Location UUID, provided if already exists", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = false)
  val id: UUID? = null,

  @param:Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @param:Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  val code: String,

  @param:Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Local name must be less than 81 characters")
  val localName: String? = null,

  @param:Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  val comments: String? = null,

  @param:Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  val orderWithinParentLocation: Int? = null,

  @param:Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  val residentialHousingType: ResidentialHousingType? = null,

  @param:Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivationReason: NomisDeactivatedReason? = null,

  @param:Schema(description = "Estimated reactivation date", example = "2025-01-05", required = false)
  val proposedReactivationDate: LocalDate? = null,

  @param:Schema(description = "Date deactivation occurred", example = "2023-01-05", required = false)
  val deactivatedDate: LocalDate? = null,

  @param:Schema(description = "Path hierarchy of the parent (if one exists)", example = "A-1", required = false)
  val parentLocationPath: String? = null,

  @param:Schema(description = "Parent UUID of the parent location (if one exists)", example = "2475f250-434a-4257-afe7-b911f1773a4e", required = false)
  val parentId: UUID? = null,

  @param:Schema(description = "Capacity details of the location", required = false)
  val capacity: Capacity? = null,

  @param:Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certifiedCell: Boolean? = null,

  @param:Schema(description = "Deprecated mechanism for displaying and updating CNA and certified status", required = false)
  @Deprecated("Use capacity and certifiedCell instead")
  val certification: Certification? = null,

  @param:Schema(description = "Location Attributes", required = false)
  val attributes: Set<ResidentialAttributeValue>? = null,

  @param:Schema(description = "Location Usage", required = false)
  val usage: Set<NonResidentialUsageDto>? = null,

  @param:Schema(description = "Indicates that this location can used for internal movements", required = false)
  val internalMovementAllowed: Boolean? = null,

  @param:Schema(description = "Date location was created, if not provided then the current time will be used for a new location", required = false)
  val createDate: LocalDateTime? = null,

  @param:Schema(description = "Last updated, if not provided then the current time will be used", required = false)
  val lastModifiedDate: LocalDateTime? = null,

  @param:Schema(description = "Username of the staff updating the location", required = true)
  val lastUpdatedBy: String,

) {

  fun toServiceTypes() = usage?.map { it.usageType.toServiceTypes() }?.flatten()?.toSet()

  fun toNewEntity(clock: Clock, linkedTransaction: LinkedTransaction): Location {
    val location = if (residentialHousingType != null) {
      if (locationType == LocationType.CELL) {
        Cell(
          id = null,
          prisonId = prisonId,
          code = code,
          locationType = locationType,
          pathHierarchy = code,
          status = if (isDeactivated()) LocationStatus.INACTIVE else LocationStatus.ACTIVE,
          localName = localName,
          residentialHousingType = residentialHousingType,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: LocalDateTime.now(clock),
          deactivatedDate = null,
          deactivatedReason = null,
          proposedReactivationDate = null,
          childLocations = sortedSetOf(),
          accommodationType = residentialHousingType.mapToAccommodationType(),
          parent = null,
          capacity = capacity?.let {
            uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity(
              maxCapacity = it.maxCapacity,
              workingCapacity = it.workingCapacity,
              certifiedNormalAccommodation = it.certifiedNormalAccommodation ?: certification?.certifiedNormalAccommodation ?: 0,
            )
          },
          certifiedCell = (certifiedCell ?: certification?.certified) == true,
        ).also {
          attributes?.forEach { attribute ->
            it.addAttribute(attribute)
            attribute.mapTo?.let { attr -> it.addSpecialistCellType(attr) }
          }
          if (it.accommodationType == AccommodationType.NORMAL_ACCOMMODATION) {
            it.addUsedFor(UsedForType.STANDARD_ACCOMMODATION, lastUpdatedBy, clock, linkedTransaction)
          }
          if (residentialHousingType == HOLDING_CELL) {
            it.convertToNonResidentialCell(convertedCellType = ConvertedCellType.HOLDING_ROOM, userOrSystemInContext = lastUpdatedBy, clock = clock, linkedTransaction = linkedTransaction)
          }
        }
      } else if (code in getVirtualLocationCodes()) {
        VirtualResidentialLocation(
          prisonId = prisonId,
          code = code,
          locationType = locationType,
          pathHierarchy = code,
          status = if (isDeactivated()) LocationStatus.INACTIVE else LocationStatus.ACTIVE,
          localName = localName,
          residentialHousingType = residentialHousingType,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: LocalDateTime.now(clock),
          capacity = capacity?.let {
            uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity(
              maxCapacity = it.maxCapacity,
              workingCapacity = it.workingCapacity,
            )
          },
          childLocations = sortedSetOf(),
        )
      } else {
        ResidentialLocation(
          prisonId = prisonId,
          code = code,
          locationType = locationType,
          pathHierarchy = code,
          status = if (isDeactivated()) LocationStatus.INACTIVE else LocationStatus.ACTIVE,
          localName = localName,
          residentialHousingType = residentialHousingType,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: LocalDateTime.now(clock),
          childLocations = sortedSetOf(),
        )
      }
    } else {
      NonResidentialLocation(
        prisonId = prisonId,
        code = code,
        locationType = locationType,
        pathHierarchy = code,
        status = if (isDeactivated()) LocationStatus.INACTIVE else LocationStatus.ACTIVE,
        localName = localName,
        comments = comments,
        orderWithinParentLocation = orderWithinParentLocation,
        internalMovementAllowed = internalMovementAllowed,
        createdBy = lastUpdatedBy,
        whenCreated = createDate ?: LocalDateTime.now(clock),
        childLocations = sortedSetOf(),
      ).also {
        usage?.forEach { usage ->
          it.addUsage(usage.usageType, usage.capacity, usage.sequence)
          usage.usageType.toServiceTypes().forEach { service -> it.addService(service) }
        }
      }
    }

    if (isDeactivated()) {
      location.deactivatedReason = deactivationReason!!.mapsTo()
      location.deactivatedDate = deactivatedDate?.atStartOfDay()
      location.proposedReactivationDate = proposedReactivationDate
    }

    return location
  }

  fun isDeactivated() = deactivationReason != null
}

enum class NomisDeactivatedReason {
  REFURBISHMENT,
  LOCAL_WORK,
  STAFF_SHORTAGE,
  MOTHBALLED,
  DAMAGED,
  NEW_BUILDING,
  CELL_RECLAIMS,
  CHANGE_OF_USE,
  CLOSURE,
  OUT_OF_USE,
  CELLS_RETURNING_TO_USE,
  OTHER,
  ;

  fun mapsTo(): DeactivatedReason = when (this) {
    NEW_BUILDING, CELL_RECLAIMS, CHANGE_OF_USE, CLOSURE, OUT_OF_USE, CELLS_RETURNING_TO_USE, OTHER -> DeactivatedReason.OTHER
    REFURBISHMENT -> DeactivatedReason.REFURBISHMENT
    LOCAL_WORK -> DeactivatedReason.MAINTENANCE
    STAFF_SHORTAGE -> DeactivatedReason.STAFF_SHORTAGE
    MOTHBALLED -> DeactivatedReason.MOTHBALLED
    DAMAGED -> DeactivatedReason.DAMAGED
  }
}
