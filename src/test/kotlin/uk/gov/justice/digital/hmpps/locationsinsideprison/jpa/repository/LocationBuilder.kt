package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.EXPECTED_USERNAME
import java.time.LocalDate
import java.time.LocalDateTime

fun buildResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType,
): ResidentialLocation {
  return ResidentialLocation(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    locationType = locationType,
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
  )
}

fun buildCell(
  prisonId: String = "MDI",
  pathHierarchy: String,
  active: Boolean = true,
  capacity: Capacity? = null,
  certification: Certification? = null,
  residentialAttributeValues: Set<ResidentialAttributeValue> = setOf(ResidentialAttributeValue.DOUBLE_OCCUPANCY, ResidentialAttributeValue.CAT_B),
  archived: Boolean = false,
): Cell {
  val cell = Cell(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    active = active,
    archived = archived,
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
      LocalDate.now(clock)
    } else {
      null
    },
  )
  cell.addAttributes(residentialAttributeValues)
  cell.addSpecialistCellType(SpecialistCellType.WHEELCHAIR_ACCESSIBLE, EXPECTED_USERNAME, clock)
  cell.addUsedFor(UsedForType.STANDARD_ACCOMMODATION, EXPECTED_USERNAME, clock)

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
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
  )
  nonResidentialLocationJPA.addUsage(nonResidentialUsageType, 15, 1)
  return nonResidentialLocationJPA
}
