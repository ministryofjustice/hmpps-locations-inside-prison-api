package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification

import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsage
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceUsage

fun filterByPrisonId(prisonId: String) = NonResidentialLocation::prisonId.buildSpecForEqualTo(prisonId)

fun filterByLocalName(localName: String) = NonResidentialLocation::localName.buildSpecForLike(localName)

fun filterByIsLeaf() = buildSpecForIsEmpty<NonResidentialLocation>("childLocations")

fun excludeByCode(code: String) = NonResidentialLocation::code.buildSpecForNotEqualTo(code)
fun excludeByLocationType(locationType: LocationType) = NonResidentialLocation::locationType.buildSpecForNotEqualTo(locationType)
fun excludeByStatus(status: LocationStatus) = NonResidentialLocation::status.buildSpecForNotEqualTo(status)

/**
 * Excludes locations that can only store property, i.e. those whose sole non-residential usage is
 * [NonResidentialUsageType.PROPERTY]. A location is kept when it has no PROPERTY usage, or when it has
 * a PROPERTY usage alongside at least one other usage (used for property *and* another service).
 */
fun excludePropertyOnlyLocations(): Specification<NonResidentialLocation> = Specification { root, query, cb ->
  fun usageExists(propertyMatch: Boolean): Predicate {
    val sub = query!!.subquery(Long::class.java)
    val usage = sub.from(NonResidentialUsage::class.java)
    sub.select(cb.literal(1L))
    val typeMatch = cb.equal(usage.get<NonResidentialUsageType>("usageType"), NonResidentialUsageType.PROPERTY)
    sub.where(
      cb.equal(usage.get<Location>("location"), root),
      if (propertyMatch) typeMatch else cb.not(typeMatch),
    )
    return cb.exists(sub)
  }
  cb.or(cb.not(usageExists(propertyMatch = true)), usageExists(propertyMatch = false))
}

fun filterByStatuses(statuses: Collection<LocationStatus>) = NonResidentialLocation::status.buildSpecForIn(statuses)
fun filterByStatuses(vararg statuses: LocationStatus) = filterByStatuses(statuses.toList())

/**
 * Matches locations on their status, treating a location a user has hidden from the list as though
 * it were archived: it is returned only when [LocationStatus.ARCHIVED] is asked for, and never
 * under its own status. The location itself is untouched - this only governs where it shows up in
 * the non-residential list.
 */
fun filterByStatusesTreatingHiddenAsArchived(statuses: Collection<LocationStatus>): Specification<NonResidentialLocation> = Specification { root, _, cb ->
  val hiddenFromList = root.get<Boolean>("hiddenFromList")
  val hidden = cb.isTrue(hiddenFromList)
  val notHidden = cb.or(cb.isNull(hiddenFromList), cb.isFalse(hiddenFromList))
  val matchesOwnStatus = cb.and(notHidden, root.get<LocationStatus>("status").`in`(statuses))

  if (LocationStatus.ARCHIVED in statuses) cb.or(hidden, matchesOwnStatus) else matchesOwnStatus
}

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
