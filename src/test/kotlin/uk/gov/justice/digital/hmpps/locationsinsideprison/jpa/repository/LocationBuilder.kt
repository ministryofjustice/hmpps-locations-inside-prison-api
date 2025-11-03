package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.generateNonResidentialCode
import java.time.LocalDateTime

fun buildResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType,
  status: LocationStatus = LocationStatus.ACTIVE,
  localName: String? = null,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
): ResidentialLocation = ResidentialLocation(
  prisonId = prisonId,
  code = pathHierarchy.split("-").last(),
  pathHierarchy = pathHierarchy,
  locationType = locationType,
  status = status,
  createdBy = EXPECTED_USERNAME,
  whenCreated = LocalDateTime.now(clock),
  childLocations = mutableListOf(),
  orderWithinParentLocation = 99,
  localName = localName,
  residentialHousingType = residentialHousingType,
)

fun buildVirtualResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  status: LocationStatus = LocationStatus.ACTIVE,
  capacity: Capacity? = null,
  localName: String? = null,
): VirtualResidentialLocation = VirtualResidentialLocation(
  prisonId = prisonId,
  code = pathHierarchy.split("-").last(),
  pathHierarchy = pathHierarchy,
  status = status,
  createdBy = EXPECTED_USERNAME,
  whenCreated = LocalDateTime.now(clock),
  childLocations = mutableListOf(),
  orderWithinParentLocation = 99,
  localName = localName,
  capacity = capacity,
)

fun buildCell(
  prisonId: String = "MDI",
  pathHierarchy: String,
  status: LocationStatus = LocationStatus.ACTIVE,
  capacity: Capacity? = null,
  certification: Certification? = null,
  residentialAttributeValues: Set<ResidentialAttributeValue> = setOf(
    ResidentialAttributeValue.DOUBLE_OCCUPANCY,
    ResidentialAttributeValue.CAT_B,
  ),
  specialistCellType: SpecialistCellType? = null,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
  linkedTransaction: LinkedTransaction,
  prisonConfiguration: PrisonConfiguration? = null,
): Cell {
  val cell = Cell(
    prisonId = prisonId,
    prisonConfiguration = prisonConfiguration,
    code = pathHierarchy.split("-").last(),
    cellMark = pathHierarchy.split("-").last(),
    status = if (status == LocationStatus.ARCHIVED) LocationStatus.INACTIVE else status,
    pathHierarchy = pathHierarchy,
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
    capacity = capacity,
    certification = certification,
    accommodationType = accommodationType,
    residentialHousingType = residentialHousingType,
    deactivatedReason = if (status != LocationStatus.ACTIVE) {
      DeactivatedReason.DAMAGED
    } else {
      null
    },
    deactivatedDate = if (status != LocationStatus.ACTIVE) {
      LocalDateTime.now(clock)
    } else {
      null
    },
  )
  cell.addAttributes(residentialAttributeValues)

  specialistCellType?.let { cell.updateCellSpecialistCellTypes(setOf(it), EXPECTED_USERNAME, clock, linkedTransaction) }
  cell.updateCellUsedFor(setOf(UsedForType.STANDARD_ACCOMMODATION), EXPECTED_USERNAME, clock, linkedTransaction)
  if (status == LocationStatus.ARCHIVED) {
    cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), EXPECTED_USERNAME, clock, linkedTransaction)
  }
  return cell
}

fun buildNonResidentialLocation(
  prisonId: String = "MDI",
  localName: String,
  pathHierarchy: String? = null,
  locationType: LocationType = LocationType.LOCATION,
  status: LocationStatus = LocationStatus.ACTIVE,
  serviceType: ServiceType,
  usageTypes: Set<NonResidentialUsageType> = emptySet(),
): NonResidentialLocation {
  val code = pathHierarchy?.split("-")?.last() ?: generateNonResidentialCode(prisonId, localName)
  val nonResidentialLocationJPA = NonResidentialLocation(
    prisonId = prisonId,
    localName = localName,
    code = code,
    pathHierarchy = code,
    locationType = locationType,
    status = status,
    createdBy = "DIFFERENT_USER",
    whenCreated = LocalDateTime.now(clock).minusDays(1),
    internalMovementAllowed = false,
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
  )
  nonResidentialLocationJPA.addService(serviceType)
  nonResidentialLocationJPA.addUsage(serviceType.nonResidentialUsageType, 15, 1)
  usageTypes.forEach { nonResidentialLocationJPA.addUsage(it, 10) }
  return nonResidentialLocationJPA
}
