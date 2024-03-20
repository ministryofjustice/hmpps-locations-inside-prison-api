package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType

interface UpdateLocationRequest {
  val code: String?
  val locationType: LocationType?
  val localName: String?
  val comments: String?
  val orderWithinParentLocation: Int?
  val residentialHousingType: ResidentialHousingType?
  val capacity: Capacity?
  val certification: Certification?
  val attributes: Set<ResidentialAttributeValue>?
  val usage: Set<NonResidentialUsageDto>?
}
