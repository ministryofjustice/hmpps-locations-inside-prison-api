package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorValue("NON_RESIDENTIAL")
class NonResidentialLocation(
  id: UUID?,
  code: String,
  pathHierarchy: String,
  locationType: LocationType,
  prisonId: String,
  parent: Location?,
  description: String?,
  comments: String?,
  orderWithinParentLocation: Int?,
  active: Boolean,
  deactivatedDate: LocalDate?,
  deactivatedReason: DeactivatedReason?,
  reactivatedDate: LocalDate?,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  whenUpdated: LocalDateTime,
  updatedBy: String,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  private var nonResidentialUsages: MutableSet<NonResidentialUsage> = mutableSetOf(),

) : Location(
  id = id,
  code = code,
  pathHierarchy = pathHierarchy,
  locationType = locationType,
  prisonId = prisonId,
  parent = parent,
  description = description,
  comments = comments,
  orderWithinParentLocation = orderWithinParentLocation,
  active = active,
  deactivatedDate = deactivatedDate,
  deactivatedReason = deactivatedReason,
  reactivatedDate = reactivatedDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  whenUpdated = whenUpdated,
  updatedBy = updatedBy,
) {

  fun addUsage(usageType: NonResidentialUsageType, capacity: Int? = null, sequence: Int = 99) {
    val existingUsage = nonResidentialUsages.find { it.usageType == usageType }
    if (existingUsage != null) {
      existingUsage.capacity = capacity
      existingUsage.sequence = sequence
    } else {
      nonResidentialUsages.add(NonResidentialUsage(location = this, usageType = usageType, capacity = capacity, sequence = sequence))
    }
  }

  override fun toDto(includeChildren: Boolean): LocationDto {
    return super.toDto(includeChildren).copy(
      usage = nonResidentialUsages.map { it.toDto() },
    )
  }

  override fun updateWith(patch: PatchLocationRequest, updatedBy: String, clock: Clock): NonResidentialLocation {
    super.updateWith(patch, updatedBy, clock)
    if (patch.usage != null) {
      patch.usage!!.map { nonResUsage ->
        this.addUsage(nonResUsage.usageType, nonResUsage.capacity, nonResUsage.sequence)
      }
    }
    return this
  }
}
