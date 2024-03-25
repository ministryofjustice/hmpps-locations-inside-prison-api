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

  @Enumerated(EnumType.STRING)
  open var residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,

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

  private fun getWorkingCapacity(): Int {
    return cellLocations().filter { it.isActiveAndAllParentsActive() }
      .sumOf { it.capacity?.workingCapacity ?: 0 }
  }

  private fun getMaxCapacity(): Int {
    return cellLocations().filter { !it.isPermanentlyClosed() }
      .sumOf { it.capacity?.maxCapacity ?: 0 }
  }

  private fun getBaselineCapacity(): Int {
    return cellLocations().filter { !it.isPermanentlyClosed() }
      .sumOf { it.certification?.capacityOfCertifiedCell ?: 0 }
  }

  private fun hasCertifiedCells(): Boolean {
    return cellLocations().filter { !it.isPermanentlyClosed() }
      .any { it.certification?.certified == true }
  }

  private fun getAttributes(): Set<ResidentialAttribute> {
    return cellLocations().filter { !it.isPermanentlyClosed() }
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
        maxCapacity = getMaxCapacity(),
        workingCapacity = getWorkingCapacity(),
      ),

      certification = CertificationDto(
        certified = hasCertifiedCells(),
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
