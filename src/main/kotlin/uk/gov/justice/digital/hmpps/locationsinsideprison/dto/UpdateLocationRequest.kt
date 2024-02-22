package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType

interface UpdateLocationRequest {
  val code: String?
  val locationType: LocationType?
  val description: String?
  val comments: String?
  val orderWithinParentLocation: Int?
  val residentialHousingType: ResidentialHousingType?
  val capacity: Capacity?
  val certification: Certification?
  val attributes: Map<ResidentialAttributeType, Set<ResidentialAttributeValue>>?
  val usage: Set<NonResidentialUsageDto>?
}
