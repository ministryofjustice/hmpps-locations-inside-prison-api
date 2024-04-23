package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SecurityCategoryType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.EXPECTED_USERNAME
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
  capacity: Capacity? = null,
  certification: Certification? = null,
  residentialAttributeValues: Set<ResidentialAttributeValue> = setOf(ResidentialAttributeValue.DOUBLE_OCCUPANCY, ResidentialAttributeValue.CAT_B),
): Cell {
  val cell = Cell(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    createdBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    childLocations = mutableListOf(),
    orderWithinParentLocation = 99,
    capacity = capacity,
    certification = certification,
    accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
    specialistCellType = SpecialistCellType.SEG,
  )
  cell.addAttributes(residentialAttributeValues)
  cell.addSecurityCategory(SecurityCategoryType.CAT_B)
  cell.addUsedFor(UsedForType.STANDARD_ACCOMMODATION)
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
