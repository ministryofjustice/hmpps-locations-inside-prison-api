package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.LocalDateTime

fun buildResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType,
  localName: String? = null,
): ResidentialLocation {
  return ResidentialLocation(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    locationType = locationType,
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
    localName = localName,
  )
}

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
    accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
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
  specialistCellType?.let { cell.updateCellSpecialistCellTypes(setOf(it), EXPECTED_USERNAME, clock) }
  cell.updateCellUsedFor(setOf(UsedForType.STANDARD_ACCOMMODATION), EXPECTED_USERNAME, clock)
  if (archived) {
    cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), EXPECTED_USERNAME, clock)
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
