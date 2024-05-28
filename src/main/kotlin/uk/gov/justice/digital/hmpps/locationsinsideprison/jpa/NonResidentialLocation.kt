package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
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
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = 1,
  active: Boolean = true,
  deactivatedDate: LocalDate? = null,
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
  active = active,
  deactivatedDate = deactivatedDate,
  deactivatedReason = deactivatedReason,
  proposedReactivationDate = proposedReactivationDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  whenUpdated = whenCreated,
  updatedBy = createdBy,
) {

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

  override fun toDto(includeChildren: Boolean, includeParent: Boolean, includeHistory: Boolean, countInactiveCells: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent, includeHistory = includeHistory, countInactiveCells = countInactiveCells).copy(
      usage = nonResidentialUsages.map { it.toDto() },
    )
  }

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation {
    return super.toLegacyDto(includeHistory = includeHistory).copy(
      usage = nonResidentialUsages.map { it.toDto() },
    )
  }

  override fun updateWith(upsert: UpdateLocationRequest, userOrSystemInContext: String, clock: Clock): NonResidentialLocation {
    super.updateWith(upsert, userOrSystemInContext, clock)
    if (upsert.usage != null) {
      recordHistoryOfUsages(upsert, userOrSystemInContext, clock)
      nonResidentialUsages.retainAll(upsert.usage!!.map { addUsage(it.usageType, it.capacity, it.sequence) }.toSet())
    }
    return this
  }

  private fun recordHistoryOfUsages(
    upsert: UpdateLocationRequest,
    updatedBy: String,
    clock: Clock,
  ) {
    val oldUsages = this.nonResidentialUsages.map { it.usageType }.toSet()
    val newUsages = (upsert.usage?.map { it.usageType } ?: emptySet()).toSet()

    newUsages.subtract(oldUsages).forEach { newAttribute ->
      addHistory(LocationAttribute.USAGE, null, newAttribute.name, updatedBy, LocalDateTime.now(clock))
      upsert.usage?.find { it.usageType == newAttribute }?.capacity?.let { capacity ->
        addHistory(LocationAttribute.NON_RESIDENTIAL_CAPACITY, null, capacity.toString(), updatedBy, LocalDateTime.now(clock))
      }
    }

    oldUsages.subtract(newUsages).forEach { removedAttribute ->
      addHistory(LocationAttribute.USAGE, removedAttribute.name, null, updatedBy, LocalDateTime.now(clock))
    }

    newUsages.intersect(oldUsages).forEach { existingType ->
      val newUsage = upsert.usage?.find { it.usageType == existingType }
      val oldUsage = this.nonResidentialUsages.find { it.usageType == existingType }
      if (newUsage != null && oldUsage != null && newUsage.capacity != oldUsage.capacity) {
        addHistory(
          LocationAttribute.NON_RESIDENTIAL_CAPACITY,
          oldUsage.capacity.toString(),
          newUsage.capacity.toString(),
          updatedBy,
          LocalDateTime.now(clock),
        )
      }
    }
    this.nonResidentialUsages
  }
}
