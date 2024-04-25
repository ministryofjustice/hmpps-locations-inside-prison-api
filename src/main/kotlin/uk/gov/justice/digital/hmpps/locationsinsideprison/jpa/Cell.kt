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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorValue("CELL")
class Cell(
  id: UUID? = null,
  code: String,
  pathHierarchy: String,
  locationType: LocationType = LocationType.CELL,
  prisonId: String,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = null,
  active: Boolean = true,
  deactivatedDate: LocalDate? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  var capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  var certification: Certification? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val attributes: MutableSet<ResidentialAttribute> = mutableSetOf(),

  @Enumerated(EnumType.STRING)
  var accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val usedFor: MutableSet<CellUsedFor> = mutableSetOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val specialistCellTypes: MutableSet<SpecialistCell> = mutableSetOf(),

) : ResidentialLocation(
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
  createdBy = createdBy,
  residentialHousingType = residentialHousingType,
) {

  fun addAttribute(attribute: ResidentialAttributeValue): ResidentialAttribute {
    val residentialAttribute = ResidentialAttribute(location = this, attributeType = attribute.type, attributeValue = attribute)
    attributes.add(residentialAttribute)
    return residentialAttribute
  }

  fun addAttributes(attributes: Set<ResidentialAttributeValue>): Set<ResidentialAttribute> {
    return attributes.map { addAttribute(it) }.toSet()
  }

  fun addSpecialistCellType(specialistCellType: SpecialistCellType): SpecialistCell {
    val specialistCell = SpecialistCell(location = this, specialistCellType = specialistCellType)
    this.specialistCellTypes.add(specialistCell)
    return specialistCell
  }

  fun addUsedFor(usedForType: UsedForType): CellUsedFor {
    val cellUsedFor = CellUsedFor(location = this, usedFor = usedForType)
    this.usedFor.add(cellUsedFor)
    return cellUsedFor
  }

  override fun updateWith(upsert: UpdateLocationRequest, updatedBy: String, clock: Clock): Cell {
    super.updateWith(upsert, updatedBy, clock)

    if (upsert.capacity != null && upsert.capacity != capacity?.toDto()) {
      addHistory(
        LocationAttribute.CAPACITY,
        capacity?.maxCapacity.toString(),
        upsert.capacity?.maxCapacity.toString(),
        updatedBy,
        LocalDateTime.now(clock),
      )
      addHistory(
        LocationAttribute.OPERATIONAL_CAPACITY,
        capacity?.workingCapacity.toString(),
        upsert.capacity?.workingCapacity.toString(),
        updatedBy,
        LocalDateTime.now(clock),
      )
    }
    this.capacity = upsert.capacity?.toNewEntity() ?: this.capacity

    if (upsert.certification != null && upsert.certification != certification?.toDto()) {
      addHistory(
        LocationAttribute.CERTIFIED,
        certification?.certified.toString(),
        upsert.certification?.certified.toString(),
        updatedBy,
        LocalDateTime.now(clock),
      )
      addHistory(
        LocationAttribute.CERTIFIED_CAPACITY,
        certification?.capacityOfCertifiedCell.toString(),
        upsert.certification?.capacityOfCertifiedCell.toString(),
        updatedBy,
        LocalDateTime.now(clock),
      )
    }
    this.certification = upsert.certification?.toNewEntity() ?: this.certification

    if (upsert.attributes != null) {
      recordHistoryOfAttributesChanges(upsert, updatedBy, clock)
      attributes.retainAll(upsert.attributes!!.map { addAttribute(it) }.toSet())
    }

    if (upsert is PatchLocationRequest) {
      if (upsert.accommodationType != null && this.accommodationType != upsert.accommodationType) {
        addHistory(
          LocationAttribute.ACCOMMODATION_TYPE,
          this.accommodationType.description,
          upsert.accommodationType.description,
          updatedBy,
          LocalDateTime.now(clock),
        )
      }
      this.accommodationType = upsert.accommodationType ?: this.accommodationType

      if (upsert.specialistCellTypes != null) {
        recordHistoryOfSecurityChanges(upsert, updatedBy, clock)
        specialistCellTypes.retainAll(upsert.specialistCellTypes.map { addSpecialistCellType(it) }.toSet())
      }

      if (upsert.usedFor != null) {
        recordHistoryOfUsedForChanges(upsert, updatedBy, clock)
        usedFor.retainAll(upsert.usedFor.map { addUsedFor(it) }.toSet())
      }
    }
    return this
  }

  private fun recordHistoryOfAttributesChanges(
    upsert: UpdateLocationRequest,
    updatedBy: String,
    clock: Clock,
  ) {
    val oldAttributes = this.attributes.map { it.attributeValue }.toSet()
    upsert.attributes?.subtract(oldAttributes)?.forEach { newAttribute ->
      addHistory(LocationAttribute.ATTRIBUTES, null, newAttribute.name, updatedBy, LocalDateTime.now(clock))
    }
    oldAttributes.subtract((upsert.attributes?.toSet() ?: emptySet()).toSet()).forEach { removedAttribute ->
      addHistory(LocationAttribute.ATTRIBUTES, removedAttribute.name, null, updatedBy, LocalDateTime.now(clock))
    }
  }

  private fun recordHistoryOfSecurityChanges(
    upsert: PatchLocationRequest,
    updatedBy: String,
    clock: Clock,
  ) {
    val oldSpecialistCellTypes = this.specialistCellTypes.map { it.specialistCellType }.toSet()
    upsert.specialistCellTypes?.subtract(oldSpecialistCellTypes)?.forEach { newSpecialistCellType ->
      addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, null, newSpecialistCellType.name, updatedBy, LocalDateTime.now(clock))
    }
    oldSpecialistCellTypes.subtract((upsert.specialistCellTypes?.toSet() ?: emptySet()).toSet()).forEach { removedSpecialistCellType ->
      addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, removedSpecialistCellType.name, null, updatedBy, LocalDateTime.now(clock))
    }
  }

  private fun recordHistoryOfUsedForChanges(
    upsert: PatchLocationRequest,
    updatedBy: String,
    clock: Clock,
  ) {
    val oldUsedFor = this.usedFor.map { it.usedFor }.toSet()
    upsert.usedFor?.subtract(oldUsedFor)?.forEach { newUsedFor ->
      addHistory(LocationAttribute.USED_FOR, null, newUsedFor.name, updatedBy, LocalDateTime.now(clock))
    }
    oldUsedFor.subtract((upsert.usedFor?.toSet() ?: emptySet()).toSet()).forEach { removedUsedFor ->
      addHistory(LocationAttribute.USED_FOR, removedUsedFor.name, null, updatedBy, LocalDateTime.now(clock))
    }
  }

  override fun toDto(includeChildren: Boolean, includeParent: Boolean, includeHistory: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent, includeHistory = includeHistory).copy(
      capacity = capacity?.toDto(),
      certification = certification?.toDto(),
    )
  }
}
