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
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CertificationException
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
  archived: Boolean = false,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  private var capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  private var certification: Certification? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val attributes: MutableSet<ResidentialAttribute> = mutableSetOf(),

  @Enumerated(EnumType.STRING)
  var accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val usedFor: MutableSet<CellUsedFor> = mutableSetOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val specialistCellTypes: MutableSet<SpecialistCell> = mutableSetOf(),

  @Enumerated(EnumType.STRING)
  var convertedCellType: ConvertedCellType? = null,

  private var otherConvertedCellType: String? = null,

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
  archived = archived
) {

  fun getWorkingCapacity() = capacity?.workingCapacity

  fun getMaxCapacity() = capacity?.maxCapacity

  fun getCapacityOfCertifiedCell() = certification?.capacityOfCertifiedCell

  fun isCertified() = certification?.certified ?: false

  fun getCapacity() = capacity?.toDto()

  fun getCertification() = certification?.toDto()

  override fun isConvertedCell() = convertedCellType != null

  fun convertToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.CONVERTED_CELL_TYPE,
      this.convertedCellType?.description,
      convertedCellType.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    this.convertedCellType = convertedCellType

    addHistory(
      LocationAttribute.ACCOMMODATION_TYPE,
      accommodationType.description,
      AccommodationType.OTHER_NON_RESIDENTIAL.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    this.accommodationType = AccommodationType.OTHER_NON_RESIDENTIAL

    if (convertedCellType == ConvertedCellType.OTHER) {
      addHistory(
        LocationAttribute.OTHER_CONVERTED_CELL_TYPE,
        accommodationType.description,
        AccommodationType.OTHER_NON_RESIDENTIAL.description,
        userOrSystemInContext,
        LocalDateTime.now(clock),
      )

      this.otherConvertedCellType = otherConvertedCellType
    }

    setCapacity(0, 0, userOrSystemInContext, clock)
    deCertifyCell(userOrSystemInContext, clock)
    recordRemovedSpecialistCellTypes(specialistCellTypes.map { it.specialistCellType }.toSet(), userOrSystemInContext, clock)
    specialistCellTypes.clear()

    recordRemovedUsedForTypes(usedFor.map { it.usedFor }.toSet(), userOrSystemInContext, clock)
    usedFor.clear()

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun convertToCell(accommodationType: AccommodationType, usedForTypes: List<UsedForType>? = null, specialistCellType: SpecialistCellType?, maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.ACCOMMODATION_TYPE,
      this.accommodationType.description,
      accommodationType.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    this.accommodationType = accommodationType

    usedForTypes?.forEach {
      addUsedFor(it, userOrSystemInContext, clock)
    }

    specialistCellType?.let {
      addSpecialistCellType(it, userOrSystemInContext, clock)
    }

    if (accommodationType == AccommodationType.NORMAL_ACCOMMODATION) {
      setCapacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity, userOrSystemInContext, clock)
      certifyCell(capacityOfCertifiedCell = workingCapacity, userOrSystemInContext, clock)
    }

    addHistory(
      LocationAttribute.CONVERTED_CELL_TYPE,
      this.convertedCellType?.description,
      null,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    convertedCellType = null

    addHistory(
      LocationAttribute.OTHER_CONVERTED_CELL_TYPE,
      this.otherConvertedCellType,
      null,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    otherConvertedCellType = null
  }

  fun setCapacity(maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock) {
    if (workingCapacity > maxCapacity) {
      throw CapacityException(workingCapacity = workingCapacity, maxCapacity = maxCapacity)
    }

    addHistory(
      LocationAttribute.CAPACITY,
      capacity?.maxCapacity?.toString(),
      maxCapacity.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    addHistory(
      LocationAttribute.OPERATIONAL_CAPACITY,
      capacity?.workingCapacity?.toString(),
      workingCapacity.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    capacity = if (maxCapacity == 0 && workingCapacity == 0) {
      null
    } else {
      Capacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity)
    }
  }

  fun certifyCell(capacityOfCertifiedCell: Int, userOrSystemInContext: String, clock: Clock) {
    if (isActive() && capacityOfCertifiedCell < 1) {
      throw CertificationException(capacityOfCertifiedCell = capacityOfCertifiedCell)
    }
    addHistory(
      LocationAttribute.CERTIFIED,
      certification?.certified?.toString(),
      true.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      capacityOfCertifiedCell.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    certification = Certification(certified = true, capacityOfCertifiedCell = capacityOfCertifiedCell)
  }

  fun deCertifyCell(userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.CERTIFIED,
      certification?.certified?.toString(),
      false.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      "0",
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    certification = Certification(certified = false, capacityOfCertifiedCell = 0)
  }

  fun addAttribute(attribute: ResidentialAttributeValue, userOrSystemInContext: String? = null, clock: Clock? = null): ResidentialAttribute {
    userOrSystemInContext?.let { addHistory(LocationAttribute.ATTRIBUTES, null, attribute.description, userOrSystemInContext, LocalDateTime.now(clock)) }
    val residentialAttribute = ResidentialAttribute(location = this, attributeType = attribute.type, attributeValue = attribute)
    attributes.add(residentialAttribute)
    return residentialAttribute
  }

  fun addAttributes(attributes: Set<ResidentialAttributeValue>): Set<ResidentialAttribute> {
    return attributes.map { addAttribute(it) }.toSet()
  }

  fun addSpecialistCellType(specialistCellType: SpecialistCellType, userOrSystemInContext: String? = null, clock: Clock? = null): SpecialistCell {
    userOrSystemInContext?.let { addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, null, specialistCellType.description, userOrSystemInContext, LocalDateTime.now(clock)) }
    val specialistCell = SpecialistCell(location = this, specialistCellType = specialistCellType)
    this.specialistCellTypes.add(specialistCell)
    return specialistCell
  }

  fun addUsedFor(usedForType: UsedForType, userOrSystemInContext: String, clock: Clock): CellUsedFor {
    addHistory(LocationAttribute.USED_FOR, null, usedForType.description, userOrSystemInContext, LocalDateTime.now(clock))
    val cellUsedFor = CellUsedFor(location = this, usedFor = usedForType)
    this.usedFor.add(cellUsedFor)
    return cellUsedFor
  }

  override fun updateWith(upsert: UpdateLocationRequest, userOrSystemInContext: String, clock: Clock): Cell {
    super.updateWith(upsert, userOrSystemInContext, clock)

    upsert.capacity?.let { setCapacity(maxCapacity = it.maxCapacity, workingCapacity = it.workingCapacity, userOrSystemInContext, clock) }

    upsert.certification?.let {
      if (it.certified) {
        certifyCell(it.capacityOfCertifiedCell, userOrSystemInContext, clock)
      } else {
        deCertifyCell(userOrSystemInContext, clock)
      }
    }

    if (upsert.attributes != null) {
      recordRemovedAttributes(upsert.attributes!!, userOrSystemInContext, clock)
      attributes.retainAll(upsert.attributes!!.map { addAttribute(it, userOrSystemInContext, clock) }.toSet())
    }

    if (upsert is PatchLocationRequest) {
      if (upsert.accommodationType != null && this.accommodationType != upsert.accommodationType) {
        addHistory(
          LocationAttribute.ACCOMMODATION_TYPE,
          this.accommodationType.description,
          upsert.accommodationType.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
        )
      }
      this.accommodationType = upsert.accommodationType ?: this.accommodationType

      if (upsert.specialistCellTypes != null) {
        recordRemovedSpecialistCellTypes(upsert.specialistCellTypes, userOrSystemInContext, clock)
        specialistCellTypes.retainAll(upsert.specialistCellTypes.map { addSpecialistCellType(it, userOrSystemInContext, clock) }.toSet())
      }

      if (upsert.usedFor != null) {
        recordRemovedUsedForTypes(upsert.usedFor, userOrSystemInContext, clock)
        usedFor.retainAll(upsert.usedFor.map { addUsedFor(it, userOrSystemInContext, clock) }.toSet())
      }
    }
    return this
  }

  private fun recordRemovedAttributes(
    attributes: Set<ResidentialAttributeValue>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldAttributes = this.attributes.map { it.attributeValue }.toSet()
    oldAttributes.subtract(attributes).forEach { removedAttribute ->
      addHistory(LocationAttribute.ATTRIBUTES, removedAttribute.name, null, userOrSystemInContext, LocalDateTime.now(clock))
    }
  }

  private fun recordRemovedSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldSpecialistCellTypes = this.specialistCellTypes.map { it.specialistCellType }.toSet()
    oldSpecialistCellTypes.subtract(specialistCellTypes).forEach { removedSpecialistCellType ->
      addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, removedSpecialistCellType.name, null, userOrSystemInContext, LocalDateTime.now(clock))
    }
  }

  private fun recordRemovedUsedForTypes(
    usedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldUsedFor = this.usedFor.map { it.usedFor }.toSet()
    oldUsedFor.subtract(usedFor).forEach { removedUsedFor ->
      addHistory(LocationAttribute.USED_FOR, removedUsedFor.name, null, userOrSystemInContext, LocalDateTime.now(clock))
    }
  }

  override fun toDto(includeChildren: Boolean, includeParent: Boolean, includeHistory: Boolean, countInactiveCells: Boolean): LocationDto {
    return super.toDto(includeChildren = includeChildren, includeParent = includeParent, includeHistory = includeHistory, countInactiveCells = countInactiveCells).copy(
      capacity = capacity?.toDto(),
      certification = certification?.toDto(),
      convertedCellType = convertedCellType,
      otherConvertedCellType = otherConvertedCellType,
    )
  }
}
