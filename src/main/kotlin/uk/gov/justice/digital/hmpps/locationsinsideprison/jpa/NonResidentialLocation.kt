package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
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
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  private val nonResidentialUsages: MutableSet<NonResidentialUsage> = mutableSetOf(),

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
  )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation = super.toLegacyDto(includeHistory = includeHistory).copy(
    usage = nonResidentialUsages.map { it.toDto() },
  )

  override fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): NonResidentialLocation {
    super.update(upsert, userOrSystemInContext, clock, linkedTransaction)

    if (upsert is PatchNonResidentialLocationRequest) {
      updateUsage(upsert.usage, userOrSystemInContext, clock, linkedTransaction)

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

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): NonResidentialLocation {
    super.sync(upsert, clock, linkedTransaction)
    updateUsage(upsert.usage, upsert.lastUpdatedBy, clock, linkedTransaction)
    return this
  }

  fun updateUsage(
    usage: Set<NonResidentialUsageDto>?,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    if (usage != null) {
      recordHistoryOfUsages(usage, userOrSystemInContext, clock, linkedTransaction)
      nonResidentialUsages.retainAll(usage.map { addUsage(it.usageType, it.capacity, it.sequence) }.toSet())
    }
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
}
