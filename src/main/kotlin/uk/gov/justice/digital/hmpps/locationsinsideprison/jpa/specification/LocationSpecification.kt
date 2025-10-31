package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification

import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceUsage

fun filterByPrisonId(prisonId: String) = NonResidentialLocation::prisonId.buildSpecForEqualTo(prisonId)

fun excludeByCode(code: String) = NonResidentialLocation::code.buildSpecForNotEqualTo(code)
fun excludeByLocationType(locationType: LocationType) = NonResidentialLocation::locationType.buildSpecForNotEqualTo(locationType)

fun filterByStatuses(statuses: Collection<LocationStatus>) = NonResidentialLocation::status.buildSpecForIn(statuses)
fun filterByStatuses(vararg statuses: LocationStatus) = filterByStatuses(statuses.toList())

fun filterByTypes(locationType: Collection<LocationType>) = NonResidentialLocation::locationType.buildSpecForIn(locationType)
fun filterByTypes(vararg locationType: LocationType) = filterByTypes(locationType.toList())

fun filterByServiceType(serviceType: ServiceType) = NonResidentialLocation::services.buildSpecForRelatedEntityPropertyEqualTo(
  ServiceUsage::serviceType,
  serviceType,
)
