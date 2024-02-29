package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
open class ResidentialLocation(
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
  proposedReactivationDate: LocalDate?,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  whenUpdated: LocalDateTime,
  updatedBy: String,

  @Enumerated(EnumType.STRING)
  var residentialHousingType: ResidentialHousingType,

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
  proposedReactivationDate = proposedReactivationDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  whenUpdated = whenUpdated,
  updatedBy = updatedBy,
) {

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

  private fun getAttributes(): Set<ResidentialAttribute> {
    return cellLocations()
      .flatMap { it.attributes }
      .toSet()
  }

  private fun cellLocations() = findAllLeafLocations().filterIsInstance<Cell>()

  override fun updateWith(upsert: UpdateLocationRequest, updatedBy: String, clock: Clock): ResidentialLocation {
    super.updateWith(upsert, updatedBy, clock)

    if (upsert.residentialHousingType != null && this.residentialHousingType != upsert.residentialHousingType) {
      addHistory(
        LocationAttribute.RESIDENTIAL_HOUSING_TYPE,
        this.residentialHousingType.name,
        upsert.residentialHousingType?.name,
        updatedBy,
        LocalDateTime.now(clock),
      )
    }
    this.residentialHousingType = upsert.residentialHousingType ?: this.residentialHousingType

    return this
  }

  override fun toDto(includeChildren: Boolean, includeParent: Boolean, includeHistory: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent, includeHistory = includeHistory).copy(
      residentialHousingType = residentialHousingType,

      capacity = CapacityDto(
        capacity = getMaxCapacity(),
        operationalCapacity = getOperationalCapacity(),
      ),

      certification = CertificationDto(
        certified = false,
        capacityOfCertifiedCell = getBaselineCapacity(),
      ),

      attributes = getAttributes().map { it.attributeValue }.distinct(),
    )
  }
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
