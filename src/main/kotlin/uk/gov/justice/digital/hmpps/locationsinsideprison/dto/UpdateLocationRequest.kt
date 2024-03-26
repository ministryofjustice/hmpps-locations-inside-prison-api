package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.LocalDate

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
  val deactivationReason: DeactivatedReason?
  val proposedReactivationDate: LocalDate?
  val deactivatedDate: LocalDate?
  fun isDeactivated() = deactivationReason != null
}
