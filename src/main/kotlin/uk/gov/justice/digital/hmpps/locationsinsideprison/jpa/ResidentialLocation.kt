package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
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
    return cellLocations().filter { it.isActiveAndAllParentsActive() || it == this }
      .sumOf { it.getWorkingCapacity() ?: 0 }
  }

  private fun getMaxCapacity(): Int {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .sumOf { it.getMaxCapacity() ?: 0 }
  }

  private fun getBaselineCapacity(): Int {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .sumOf { it.getCapacityOfCertifiedCell() ?: 0 }
  }

  private fun hasCertifiedCells(): Boolean {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .any { it.isCertified() }
  }

  private fun getAttributes(): Set<ResidentialAttribute> {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .flatMap { it.attributes }
      .toSet()
  }

  fun getAccommodationTypes(): Set<AccommodationType> {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .map { it.accommodationType }
      .toSet()
  }

  private fun getUsedFor(): Set<CellUsedFor> {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .flatMap { it.usedFor }
      .toSet()
  }

  private fun getSpecialistCellTypes(): Set<SpecialistCell> {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .flatMap { it.specialistCellTypes }
      .toSet()
  }
  private fun isCurrentCellOrNotPermanentlyInactive(cell: Cell) = !cell.isPermanentlyDeactivated() || cell == this

  open fun isConvertedCell(): Boolean = false

  private fun getInactiveCellCount() = cellLocations().count { !it.isActive() }

  open fun update(upsert: PatchResidentialLocationRequest, userOrSystemInContext: String, clock: Clock): ResidentialLocation {
    updateCode(upsert.code, userOrSystemInContext, clock)
    return this
  }

  override fun sync(upsert: NomisSyncLocationRequest, userOrSystemInContext: String, clock: Clock): ResidentialLocation {
    super.sync(upsert, updatedBy, clock)

    addHistory(
      LocationAttribute.RESIDENTIAL_HOUSING_TYPE,
      this.residentialHousingType.name,
      upsert.residentialHousingType?.name,
      updatedBy,
      LocalDateTime.now(clock),
    )
    upsert.residentialHousingType?.let {
      this.residentialHousingType = it
    }

    return this
  }

  override fun toDto(includeChildren: Boolean, includeParent: Boolean, includeHistory: Boolean, countInactiveCells: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent, includeHistory = includeHistory, countInactiveCells = countInactiveCells).copy(

      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = getWorkingCapacity(),
      ),

      certification = CertificationDto(
        certified = hasCertifiedCells(),
        capacityOfCertifiedCell = getBaselineCapacity(),
      ),

      accommodationTypes = getAccommodationTypes().map { it }.distinct(),
      usedFor = getUsedFor().map { it.usedFor }.distinct(),

      specialistCellTypes = getSpecialistCellTypes().map { it.specialistCellType }.distinct(),
      inactiveCells = if (countInactiveCells) {
        getInactiveCellCount()
      } else {
        null
      },
    )
  }

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation {
    return super.toLegacyDto(includeHistory = includeHistory).copy(
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
