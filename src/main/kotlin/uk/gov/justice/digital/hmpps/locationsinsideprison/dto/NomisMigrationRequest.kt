package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType.HOLDING_CELL
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity as CapacityJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification as CertificationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

interface NomisMigrationRequest : UpdateLocationRequest {
  fun toNewEntity(clock: Clock): LocationJPA

  val prisonId: String
  override val code: String
  override val locationType: LocationType
  override val localName: String?
  override val comments: String?
  override val orderWithinParentLocation: Int?
  override val residentialHousingType: ResidentialHousingType?
  val capacity: Capacity?
  val certification: Certification?
  override val attributes: Set<ResidentialAttributeValue>?
  override val usage: Set<NonResidentialUsageDto>?
  val parentId: UUID?
  val parentLocationPath: String?
  val deactivationReason: NomisDeactivatedReason?
  val proposedReactivationDate: LocalDate?
  val deactivatedDate: LocalDate?
  val createDate: LocalDateTime?
  val lastModifiedDate: LocalDateTime?
  val lastUpdatedBy: String

  fun createLocation(clock: Clock): Location {
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
          residentialHousingType = residentialHousingType!!,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: LocalDateTime.now(clock),
          deactivatedDate = null,
          deactivatedReason = null,
          proposedReactivationDate = null,
          childLocations = mutableListOf(),
          accommodationType = mapAccommodationType(residentialHousingType!!),
          parent = null,
          capacity = capacity?.let {
            CapacityJPA(
              maxCapacity = it.maxCapacity,
              workingCapacity = it.workingCapacity,
            )
          },
          certification = certification?.let {
            CertificationJPA(
              certified = it.certified,
              capacityOfCertifiedCell = it.capacityOfCertifiedCell,
            )
          },
        )
        attributes?.forEach { attribute ->
          location.addAttribute(attribute)
          attribute.mapTo?.let { location.addSpecialistCellType(it) }
        }
        location.addUsedFor(UsedForType.STANDARD_ACCOMMODATION, lastUpdatedBy, clock)
        if (residentialHousingType == HOLDING_CELL) {
          location.convertToNonResidentialCell(convertedCellType = ConvertedCellType.HOLDING_ROOM, userOrSystemInContext = lastUpdatedBy, clock = clock)
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
          residentialHousingType = residentialHousingType!!,
          comments = comments,
          orderWithinParentLocation = orderWithinParentLocation,
          createdBy = lastUpdatedBy,
          whenCreated = createDate ?: LocalDateTime.now(clock),
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
        whenCreated = createDate ?: LocalDateTime.now(clock),
        childLocations = mutableListOf(),
      )
      usage?.forEach { usage ->
        location.addUsage(usage.usageType, usage.capacity, usage.sequence)
      }
      location
    }

    if (isDeactivated()) {
      location.deactivatedReason = deactivationReason!!.mapsTo()
      location.deactivatedDate = deactivatedDate
      location.proposedReactivationDate = proposedReactivationDate
    }

    return location
  }

  fun isDeactivated() = deactivationReason != null

  fun mapAccommodationType(residentialHousingType: ResidentialHousingType): AccommodationType {
    return when (residentialHousingType) {
      ResidentialHousingType.NORMAL_ACCOMMODATION -> AccommodationType.NORMAL_ACCOMMODATION
      ResidentialHousingType.HEALTHCARE -> AccommodationType.HEALTHCARE_INPATIENTS
      ResidentialHousingType.SEGREGATION -> AccommodationType.CARE_AND_SEPARATION

      ResidentialHousingType.SPECIALIST_CELL,
      HOLDING_CELL,
      ResidentialHousingType.OTHER_USE,
      ResidentialHousingType.RECEPTION,
      -> AccommodationType.OTHER_NON_RESIDENTIAL
    }
  }
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
