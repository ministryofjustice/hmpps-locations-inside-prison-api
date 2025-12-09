package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.formatLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialLocationDTO
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorValue("NON_RESIDENTIAL")
class NonResidentialLocation(
  id: UUID? = null,
  code: String,
  pathHierarchy: String,
  locationType: LocationType,
  prisonId: String,
  status: LocationStatus,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = 1,
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: SortedSet<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  private val nonResidentialUsages: SortedSet<NonResidentialUsage> = sortedSetOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  val services: SortedSet<ServiceUsage> = sortedSetOf(),

  private var internalMovementAllowed: Boolean? = false,
) : Location(
  id = id,
  code = code,
  pathHierarchy = pathHierarchy,
  locationType = locationType,
  prisonId = prisonId,
  parent = parent,
  localName = localName,
  comments = comments,
  orderWithinParentLocation = orderWithinParentLocation,
  status = status,
  deactivatedDate = deactivatedDate,
  deactivatedReason = deactivatedReason,
  proposedReactivationDate = proposedReactivationDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  whenUpdated = whenCreated,
  updatedBy = createdBy,
) {

  fun toUsageTypes() = nonResidentialUsages.map { it.usageType }

  override fun isNonResidential() = true

  override fun hasDeactivatedParent() = false

  override fun findDeactivatedLocationInHierarchy(): Location? {
    if (isActive()) {
      return this
    }
    return null
  }

  fun addUsage(usageType: NonResidentialUsageType, capacity: Int? = null, sequence: Int = 99): NonResidentialUsage {
    val existingUsage = nonResidentialUsages.find { it.usageType == usageType }
    if (existingUsage != null) {
      existingUsage.capacity = capacity
      existingUsage.sequence = sequence
      return existingUsage
    } else {
      val nonResidentialUsage =
        NonResidentialUsage(location = this, usageType = usageType, capacity = capacity, sequence = sequence)
      nonResidentialUsages.add(nonResidentialUsage)
      return nonResidentialUsage
    }
  }

  fun addService(serviceType: ServiceType): ServiceUsage {
    val existingService = services.find { it.serviceType == serviceType }
    if (existingService == null) {
      val serviceUsage = ServiceUsage(location = this, serviceType = serviceType)
      services.add(serviceUsage)
      return serviceUsage
    }
    return existingService
  }

  fun toNonResidentialDto(): NonResidentialLocationDTO {
    val deactivatedLocation = findDeactivatedLocationInHierarchy()

    return NonResidentialLocationDTO(
      id = id!!,
      localName = localName?.let { formatLocation(it) } ?: getPathHierarchy(),
      code = getLocationCode(),
      status = getDerivedStatus(),
      locationType = locationType,
      pathHierarchy = getPathHierarchy(),
      prisonId = prisonId,
      parentId = getParent()?.id,
      level = getLevel(),
      permanentlyInactive = isPermanentlyDeactivated(),
      deactivatedDate = deactivatedLocation?.deactivatedDate,
      deactivatedReason = deactivatedLocation?.deactivatedReason,
      deactivationReasonDescription = deactivatedLocation?.deactivationReasonDescription,
      deactivatedBy = deactivatedBy,
      usedByGroupedServices = services.map { it.serviceType.serviceFamily }.distinct().sortedBy { it.sequence },
      usedByServices = services.map { it.serviceType }.sortedBy { it.sequence },
    )
  }

  override fun toDto(
    includeChildren: Boolean,
    includeParent: Boolean,
    includeHistory: Boolean,
    countInactiveCells: Boolean,
    includeNonResidential: Boolean,
    useHistoryForUpdate: Boolean,
    countCells: Boolean,
    formatLocalName: Boolean,
  ): LocationDto = super.toDto(
    includeChildren = includeChildren,
    includeParent = includeParent,
    includeHistory = includeHistory,
    countInactiveCells = countInactiveCells,
    includeNonResidential = includeNonResidential,
    useHistoryForUpdate = useHistoryForUpdate,
    countCells = countCells,
    formatLocalName = formatLocalName,
  ).copy(
    usage = nonResidentialUsages.map { it.toDto() }.sortedBy { it.usageType.sequence },
    servicesUsingLocation = services.map { it.toDto() }.sortedBy { it.serviceType.sequence },
    internalMovementAllowed = internalMovementAllowed,
  )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation = super.toLegacyDto(includeHistory = includeHistory).copy(
    usage = nonResidentialUsages.map { it.toDto() },
    internalMovementAllowed = internalMovementAllowed,
  )

  override fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): NonResidentialLocation {
    super.update(upsert, userOrSystemInContext, clock, linkedTransaction)

    if (upsert is PatchNonResidentialLocationRequest) {
      upsert.servicesUsingLocation?.let {
        updateServices(it, userOrSystemInContext, clock, linkedTransaction)
        updateUsage(buildNonResidentialUsageFromService(upsert.toUsages()), userOrSystemInContext, clock, linkedTransaction)
      }
      val internalMovementAllowedUpdate = services.find { it.serviceType == ServiceType.INTERNAL_MOVEMENTS } != null
      if (internalMovementAllowed != internalMovementAllowedUpdate) {
        addHistory(
          attributeName = LocationAttribute.INTERNAL_MOVEMENT_ALLOWED,
          oldValue = this.internalMovementAllowed.toString(),
          newValue = internalMovementAllowedUpdate.toString(),
          amendedBy = userOrSystemInContext,
          amendedDate = LocalDateTime.now(clock),
          linkedTransaction = linkedTransaction,
        )
        this.internalMovementAllowed = internalMovementAllowedUpdate
      }

      if (upsert.locationType != null) {
        addHistory(
          LocationAttribute.LOCATION_TYPE,
          getDerivedLocationType().description,
          upsert.locationType.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
        this.locationType = upsert.locationType.baseType
      }
    }

    return this
  }

  private fun buildNonResidentialUsageFromService(derivedUsages: Set<NonResidentialUsageType>): Set<NonResidentialUsageDto> {
    val newSetOfUsages = mutableSetOf<NonResidentialUsageDto>()

    val newUsages = derivedUsages.filter { it !in this.toUsageTypes() }
    newSetOfUsages.addAll(newUsages.map { NonResidentialUsageDto(it, 99) })

    val existingUsages = this.nonResidentialUsages.filter { it.usageType in derivedUsages }
    newSetOfUsages.addAll(existingUsages.map { NonResidentialUsageDto(it.usageType, it.capacity, it.sequence) })

    // These are things like PROPERTY and OTHER - we don't want to lose them if they aren't mapped to a service
    val unmappedUsages = this.nonResidentialUsages.filter { it.usageType !in ServiceType.entries.map { st -> st.nonResidentialUsageType } }
    newSetOfUsages.addAll(unmappedUsages.map { NonResidentialUsageDto(it.usageType, it.capacity, it.sequence) })

    return newSetOfUsages.toSet()
  }

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): NonResidentialLocation {
    super.sync(upsert, clock, linkedTransaction)
    upsert.usage?.let { usages ->
      updateUsage(usages, upsert.lastUpdatedBy, clock, linkedTransaction)
    }
    upsert.toServiceTypes()?.let { services ->
      updateServices(services, upsert.lastUpdatedBy, clock, linkedTransaction)
    }

    upsert.internalMovementAllowed?.let { internalMovementAllowedUpdate ->
      addHistory(
        attributeName = LocationAttribute.INTERNAL_MOVEMENT_ALLOWED,
        oldValue = this.internalMovementAllowed.toString(),
        newValue = internalMovementAllowedUpdate.toString(),
        amendedBy = upsert.lastUpdatedBy,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )

      this.internalMovementAllowed = internalMovementAllowedUpdate
    }
    return this
  }

  fun updateUsage(
    usage: Set<NonResidentialUsageDto>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    recordHistoryOfUsages(usage, userOrSystemInContext, clock, linkedTransaction)
    nonResidentialUsages.retainAll(usage.map { addUsage(it.usageType, it.capacity, it.sequence) }.toSet())
  }

  fun updateServices(
    serviceTypes: Set<ServiceType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    recordHistoryOfServices(serviceTypes, userOrSystemInContext, clock, linkedTransaction)
    services.retainAll(serviceTypes.map { service -> addService(service) }.toSet())
  }

  private fun recordHistoryOfUsages(
    usage: Set<NonResidentialUsageDto>,
    updatedBy: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldUsages = this.nonResidentialUsages.map { it.usageType }.toSet()
    val newUsages = usage.map { it.usageType }.toSet()

    newUsages.subtract(oldUsages).forEach { newAttribute ->
      addHistory(LocationAttribute.USAGE, null, newAttribute.description, updatedBy, LocalDateTime.now(clock), linkedTransaction)
      usage.find { it.usageType == newAttribute }?.capacity?.let { capacity ->
        addHistory(LocationAttribute.NON_RESIDENTIAL_CAPACITY, null, capacity.toString(), updatedBy, LocalDateTime.now(clock), linkedTransaction)
      }
    }

    oldUsages.subtract(newUsages).forEach { removedAttribute ->
      addHistory(LocationAttribute.USAGE, removedAttribute.description, null, updatedBy, LocalDateTime.now(clock), linkedTransaction)
    }

    newUsages.intersect(oldUsages).forEach { existingType ->
      val newUsage = usage.find { it.usageType == existingType }
      val oldUsage = this.nonResidentialUsages.find { it.usageType == existingType }
      if (newUsage != null && oldUsage != null && newUsage.capacity != oldUsage.capacity) {
        addHistory(
          LocationAttribute.NON_RESIDENTIAL_CAPACITY,
          oldUsage.capacity.toString(),
          newUsage.capacity.toString(),
          updatedBy,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
    this.nonResidentialUsages
  }

  private fun recordHistoryOfServices(
    newServices: Set<ServiceType>,
    updatedBy: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldServices = this.services.map { it.serviceType }.toSet()

    newServices.subtract(oldServices).forEach { newAttribute ->
      addHistory(LocationAttribute.USED_BY_SERVICE, null, newAttribute.description, updatedBy, LocalDateTime.now(clock), linkedTransaction)
    }

    oldServices.subtract(newServices).forEach { removedAttribute ->
      addHistory(LocationAttribute.USED_BY_SERVICE, removedAttribute.description, null, updatedBy, LocalDateTime.now(clock), linkedTransaction)
    }

    this.services
  }
}
