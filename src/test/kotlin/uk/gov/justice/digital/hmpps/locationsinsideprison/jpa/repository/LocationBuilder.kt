package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import java.time.LocalDateTime
import java.util.*

fun buildResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType,
  localName: String? = null,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
): ResidentialLocation = ResidentialLocation(
  prisonId = prisonId,
  code = pathHierarchy.split("-").last(),
  pathHierarchy = pathHierarchy,
  locationType = locationType,
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
  capacity: Capacity? = null,
  localName: String? = null,
): VirtualResidentialLocation = VirtualResidentialLocation(
  prisonId = prisonId,
  code = pathHierarchy.split("-").last(),
  pathHierarchy = pathHierarchy,
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
  active: Boolean = true,
  capacity: Capacity? = null,
  certification: Certification? = null,
  residentialAttributeValues: Set<ResidentialAttributeValue> = setOf(
    ResidentialAttributeValue.DOUBLE_OCCUPANCY,
    ResidentialAttributeValue.CAT_B,
  ),
  specialistCellType: SpecialistCellType? = null,
  archived: Boolean = false,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
  linkedTransaction: LinkedTransaction,
): Cell {
  val cell = Cell(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    active = active,
    pathHierarchy = pathHierarchy,
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
    capacity = capacity,
    certification = certification,
    accommodationType = accommodationType,
    residentialHousingType = residentialHousingType,
    deactivatedReason = if (!active) {
      DeactivatedReason.DAMAGED
    } else {
      null
    },
    deactivatedDate = if (!active) {
      LocalDateTime.now(clock)
    } else {
      null
    },
  )
  cell.addAttributes(residentialAttributeValues)

  specialistCellType?.let { cell.updateCellSpecialistCellTypes(setOf(it), EXPECTED_USERNAME, clock, linkedTransaction) }
  cell.updateCellUsedFor(setOf(UsedForType.STANDARD_ACCOMMODATION), EXPECTED_USERNAME, clock, linkedTransaction)
  if (archived) {
    cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), EXPECTED_USERNAME, clock, linkedTransaction)
  }
  return cell
}

fun buildNonResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType,
  nonResidentialUsageType: NonResidentialUsageType,
): NonResidentialLocation {
  val nonResidentialLocationJPA = NonResidentialLocation(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    locationType = locationType,
    createdBy = "DIFFERENT_USER",
    whenCreated = LocalDateTime.now(clock).minusDays(1),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
  )
  nonResidentialLocationJPA.addUsage(nonResidentialUsageType, 15, 1)
  return nonResidentialLocationJPA
}
