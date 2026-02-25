package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification

import jakarta.persistence.criteria.JoinType
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceUsage

fun filterByPrisonId(prisonId: String) = NonResidentialLocation::prisonId.buildSpecForEqualTo(prisonId)

fun filterByLocalName(localName: String) = NonResidentialLocation::localName.buildSpecForLike(localName)

fun filterByIsLeaf() = buildSpecForIsEmpty<NonResidentialLocation>("childLocations")

fun excludeByCode(code: String) = NonResidentialLocation::code.buildSpecForNotEqualTo(code)
fun excludeByLocationType(locationType: LocationType) = NonResidentialLocation::locationType.buildSpecForNotEqualTo(locationType)
fun excludeByStatus(status: LocationStatus) = NonResidentialLocation::status.buildSpecForNotEqualTo(status)

fun filterByStatuses(statuses: Collection<LocationStatus>) = NonResidentialLocation::status.buildSpecForIn(statuses)
fun filterByStatuses(vararg statuses: LocationStatus) = filterByStatuses(statuses.toList())

fun filterByTypes(locationType: Collection<LocationType>) = NonResidentialLocation::locationType.buildSpecForIn(locationType)
fun filterByTypes(vararg locationType: LocationType) = filterByTypes(locationType.toList())

fun filterByServiceTypes(serviceTypes: Collection<ServiceType>): Specification<NonResidentialLocation> = Specification { root, query, criteriaBuilder ->
  if (serviceTypes.isEmpty()) {
    return@Specification criteriaBuilder.conjunction()
  }

  // Joining a @OneToMany produces duplicates unless we mark the query as distinct.
  query.distinct(true)

  val servicesJoin = root.join<NonResidentialLocation, ServiceUsage>("services", JoinType.INNER)
  servicesJoin.get<ServiceType>("serviceType").`in`(serviceTypes)
}
fun filterByServiceTypes(vararg serviceTypes: ServiceType) = filterByServiceTypes(serviceTypes.toList())
