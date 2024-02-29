package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
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
    updatedBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    whenUpdated = LocalDateTime.now(TestBase.clock),
    deactivatedDate = null,
    deactivatedReason = null,
    proposedReactivationDate = null,
    childLocations = mutableListOf(),
    parent = null,
    active = true,
    description = null,
    comments = null,
    orderWithinParentLocation = 99,
    residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
    id = null,
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
    locationType = LocationType.CELL,
    updatedBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    whenUpdated = LocalDateTime.now(TestBase.clock),
    deactivatedDate = null,
    deactivatedReason = null,
    proposedReactivationDate = null,
    childLocations = mutableListOf(),
    parent = null,
    active = true,
    description = null,
    comments = null,
    orderWithinParentLocation = 99,
    residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
    capacity = capacity,
    certification = certification,
    id = null,
  )
  cell.addAttributes(residentialAttributeValues)
  return cell
}
 fun buildNonResidentialLocation(
  prisonId: String = "MDI",
  pathHierarchy: String,
  locationType: LocationType = LocationType.CELL,
  nonResidentialUsageType: NonResidentialUsageType,
): NonResidentialLocation {
  val nonResidentialLocationJPA = NonResidentialLocation(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    locationType = locationType,
    updatedBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(TestBase.clock),
    whenUpdated = LocalDateTime.now(TestBase.clock),
    deactivatedDate = null,
    deactivatedReason = null,
    reactivatedDate = null,
    childLocations = mutableListOf(),
    parent = null,
    active = true,
    description = null,
    comments = null,
    orderWithinParentLocation = 99,
    id = null,
  )
  nonResidentialLocationJPA.addUsage(nonResidentialUsageType, 15, 1)
  return nonResidentialLocationJPA
}