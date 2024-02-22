package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification as CertificationDto
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
  var capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
  var certification: Certification? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  private var attributes: MutableSet<ResidentialAttribute> = mutableSetOf(),

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

  fun addAttribute(attribute: ResidentialAttributeValue): ResidentialAttribute {
    val residentialAttribute = ResidentialAttribute(location = this, attributeType = attribute.type, attributeValue = attribute)
    attributes.add(residentialAttribute)
    return residentialAttribute
  }

  private fun getOperationalCapacity(): Int {
    return cellLocations()
      .sumOf { it.capacity?.operationalCapacity ?: 0 }
  }

  private fun getMaxCapacity(): Int {
    return cellLocations()
      .sumOf { it.capacity?.capacity ?: 0 }
  }

  private fun getBaselineCapacity(): Int {
    return cellLocations()
      .sumOf { it.certification?.capacityOfCertifiedCell ?: 0 }
  }

  private fun cellLocations() = findAllLeafLocations().filterIsInstance<ResidentialLocation>().filter { it.isCell() }

  override fun updateWith(upsert: UpdateLocationRequest, updatedBy: String, clock: Clock): ResidentialLocation {
    super.updateWith(upsert, updatedBy, clock)
    this.residentialHousingType = upsert.residentialHousingType ?: this.residentialHousingType
    this.capacity = upsert.capacity?.toNewEntity() ?: this.capacity
    this.certification = upsert.certification?.toNewEntity() ?: this.certification
    this.attributes = upsert.attributes?.map { attribute ->
      this.addAttribute(attribute)
    }?.toMutableSet() ?: this.attributes
    return this
  }

  override fun toDto(includeChildren: Boolean, includeParent: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent).copy(
      capacity = if (isCell()) {
        capacity?.toDto()
      } else {
        CapacityDto(
          capacity = getMaxCapacity(),
          operationalCapacity = getOperationalCapacity(),
        )
      },
      certification = if (isCell()) {
        certification?.toDto()
      } else {
        CertificationDto(
          certified = false,
          capacityOfCertifiedCell = getBaselineCapacity(),
        )
      },
      residentialHousingType = residentialHousingType,
      attributes = attributes.map { it.attributeValue },
    )
  }
}

enum class ResidentialLocationType(
  val description: String,
) {
  WING("Wing"),
  SPUR("Spur"),
  LANDING("Landing"),
  CELL("Cell"),
  ROOM("Room"),
  HOLDING_AREA("Holding Area"),
  TIER("Tier"),
  MOVEMENT_AREA("Movement Area"),
  RESIDENTIAL_UNIT("Residential Unit"),
  EXTERNAL_GROUNDS("External Grounds"),
  HOLDING_CELL("Holding Cell"),
  MEDICAL("Medical"),
}

enum class ResidentialHousingType(
  val description: String,
) {
  NORMAL_ACCOMMODATION("Normal Accommodation"),
  HEALTHCARE("Healthcare"),
  HOLDING_CELL("Holding Cell"),
  OTHER_USE("Other Use"),
  RECEPTION("Reception"),
  SEGREGATION("Segregation"),
  SPECIALIST_CELL("Specialist Cell"),
}
