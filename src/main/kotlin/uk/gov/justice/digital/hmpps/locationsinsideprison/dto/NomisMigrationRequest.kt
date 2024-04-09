package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
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
  override val deactivationReason: DeactivatedReason?
  override val proposedReactivationDate: LocalDate?
  override val deactivatedDate: LocalDate?
  override val capacity: Capacity?
  override val certification: Certification?
  override val attributes: Set<ResidentialAttributeValue>?
  override val usage: Set<NonResidentialUsageDto>?
  val parentId: UUID?
  val parentLocationPath: String?
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
      location.deactivatedReason = deactivationReason
      location.deactivatedDate = deactivatedDate
      location.proposedReactivationDate = proposedReactivationDate
    }

    return location
  }
}
