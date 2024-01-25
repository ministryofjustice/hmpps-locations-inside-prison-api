package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorValue("RESIDENTIAL")
class ResidentialLocation(
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

  @Enumerated(EnumType.STRING)
  var residentialHousingType: ResidentialHousingType,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
  val capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
  val certification: Certification? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  private var attributes: MutableList<LocationAttribute> = mutableListOf(),

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

  fun addAttribute(attribute: LocationAttributeValue) {
    attributes.add(LocationAttribute(location = this, type = attribute.type, value = attribute))
  }

  override fun updateWith(patch: PatchLocationRequest, updatedBy: String, clock: Clock): ResidentialLocation {
    super.updateWith(patch, updatedBy, clock)
    this.residentialHousingType = patch.residentialHousingType ?: this.residentialHousingType

    return this
  }

  override fun toDto(includeChildren: Boolean): LocationDto {
    return super.toDto(includeChildren).copy(
      capacity = capacity?.capacity,
      operationalCapacity = capacity?.operationalCapacity,
      certified = certification?.certified,
      capacityOfCertifiedCell = certification?.capacityOfCertifiedCell,
      residentialHousingType = residentialHousingType,
      attributes = attributes.groupBy { it.type }.mapValues { type -> type.value.map { it.value } },
    )
  }
}
