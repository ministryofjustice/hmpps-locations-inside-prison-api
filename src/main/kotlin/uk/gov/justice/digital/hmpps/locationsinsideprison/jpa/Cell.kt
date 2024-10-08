package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.annotations.BatchSize
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
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
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  private var capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  private var certification: Certification? = null,

  @BatchSize(size = 10)
  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val attributes: MutableSet<ResidentialAttribute> = mutableSetOf(),

  @Enumerated(EnumType.STRING)
  var accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @BatchSize(size = 100)
  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val usedFor: MutableSet<CellUsedFor> = mutableSetOf(),

  @BatchSize(size = 100)
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
) {

  fun getWorkingCapacity() = capacity?.workingCapacity

  fun getMaxCapacity() = capacity?.maxCapacity

  fun getCapacityOfCertifiedCell() = certification?.capacityOfCertifiedCell

  fun setCapacityOfCertifiedCell(capacityOfCertifiedCell: Int, userOrSystemInContext: String, clock: Clock): Boolean {
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      capacityOfCertifiedCell.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    if (certification != null) {
      certification!!.capacityOfCertifiedCell = capacityOfCertifiedCell
      return true
    }
    return false
  }

  fun isCertified() = certification?.certified == true

  override fun getDerivedLocationType() = if (isConvertedCell()) {
    LocationType.ROOM
  } else {
    locationType
  }

  override fun isConvertedCell() = convertedCellType != null

  private fun getConvertedCellTypeSummary() = listOfNotNull(convertedCellType?.description, otherConvertedCellType).joinToString(" - ")

  fun convertToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock) {
    updateNonResidentialCellType(convertedCellType, otherConvertedCellType, userOrSystemInContext, clock)
    addHistory(
      LocationAttribute.LOCATION_TYPE,
      locationType.name,
      getDerivedLocationType().name,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    setAccommodationTypeForCell(AccommodationType.OTHER_NON_RESIDENTIAL, userOrSystemInContext, clock)

    addHistory(
      LocationAttribute.CAPACITY,
      capacity?.maxCapacity?.toString(),
      null,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    addHistory(
      LocationAttribute.OPERATIONAL_CAPACITY,
      capacity?.workingCapacity?.toString(),
      null,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    capacity = null
    deCertifyCell(userOrSystemInContext, clock)
    recordRemovedSpecialistCellTypes(specialistCellTypes.map { it.specialistCellType }.toSet(), userOrSystemInContext, clock)
    specialistCellTypes.clear()

    recordRemovedUsedForTypes(usedFor.map { it.usedFor }.toSet(), userOrSystemInContext, clock)
    usedFor.clear()
  }

  fun updateNonResidentialCellType(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.CONVERTED_CELL_TYPE,
      this.getConvertedCellTypeSummary(),
      listOfNotNull(convertedCellType.description, otherConvertedCellType).joinToString(" - "),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    this.convertedCellType = convertedCellType
    if (convertedCellType == ConvertedCellType.OTHER) {
      this.otherConvertedCellType = otherConvertedCellType
    } else {
      this.otherConvertedCellType = null
    }

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun convertToNonCell() {
    attributes.clear()
    usedFor.clear()
    specialistCellTypes.clear()
    capacity = null
    certification = null
  }

  fun convertToCell(accommodationType: AllowedAccommodationTypeForConversion, usedForTypes: List<UsedForType>? = null, specialistCellTypes: Set<SpecialistCellType>? = null, maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.CONVERTED_CELL_TYPE,
      this.getConvertedCellTypeSummary(),
      "Cell",
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    convertedCellType = null
    otherConvertedCellType = null

    setAccommodationTypeForCell(accommodationType.mapsTo, userOrSystemInContext, clock)

    usedForTypes?.forEach {
      addUsedFor(it, userOrSystemInContext, clock)
    }

    specialistCellTypes?.let { updateSpecialistCellTypes(specialistCellTypes = it, clock = clock, userOrSystemInContext = userOrSystemInContext) }

    setCapacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity, userOrSystemInContext, clock)
    certifyCell(userOrSystemInContext, clock)

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun setCapacity(maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock) {
    if (isCell()) {
      if (workingCapacity > 99) {
        throw CapacityException(
          getKey(),
          "Working capacity must be less than 100",
          ErrorCode.WorkingCapacityLimitExceeded,
        )
      }
      if (maxCapacity > 99) {
        throw CapacityException(getKey(), "Max capacity must be less than 100", ErrorCode.MaxCapacityLimitExceeded)
      }
      if (workingCapacity > maxCapacity) {
        throw CapacityException(
          getKey(),
          "Working capacity ($workingCapacity) cannot be more than max capacity ($maxCapacity)",
          ErrorCode.WorkingCapacityExceedsMaxCapacity,
        )
      }
      if (maxCapacity == 0 && !isPermanentlyDeactivated()) {
        throw CapacityException(getKey(), "Max capacity cannot be zero", ErrorCode.MaxCapacityCannotBeZero)
      }
      if (!(isPermanentlyDeactivated() || isTemporarilyDeactivated()) && workingCapacity == 0 && accommodationType == AccommodationType.NORMAL_ACCOMMODATION && specialistCellTypes.isEmpty()) {
        throw CapacityException(
          getKey(),
          "Cannot have a 0 working capacity with normal accommodation and not specialist cell",
          ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
        )
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

      log.info("${getKey()}: Updating max capacity from ${capacity?.maxCapacity ?: 0} to $maxCapacity and working capacity from ${capacity?.workingCapacity ?: 0} to $workingCapacity")
      if (capacity != null) {
        capacity?.setCapacity(maxCapacity, workingCapacity)
      } else {
        capacity = Capacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity)
      }

      this.updatedBy = userOrSystemInContext
      this.whenUpdated = LocalDateTime.now(clock)
    } else {
      log.warn("Capacity cannot be set on a converted cell")
    }
  }

  fun certifyCell(userOrSystemInContext: String, clock: Clock) {
    val oldCertification = getCertifiedSummary(this.certification)
    if (certification != null) {
      certification?.setCertification(true, certification?.capacityOfCertifiedCell ?: 0)
    } else {
      certification = Certification(certified = true, capacityOfCertifiedCell = 0)
    }

    addHistory(
      LocationAttribute.CERTIFIED,
      oldCertification,
      getCertifiedSummary(this.certification),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun deCertifyCell(userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.CERTIFIED,
      getCertifiedSummary(this.certification),
      "Uncertified",
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      (certification?.capacityOfCertifiedCell ?: 0).toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )

    if (certification != null) {
      certification?.setCertification(false, certification?.capacityOfCertifiedCell ?: 0)
    } else {
      certification = Certification(certified = false, capacityOfCertifiedCell = 0)
    }

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun addAttribute(attribute: ResidentialAttributeValue, userOrSystemInContext: String? = null, clock: Clock? = null): ResidentialAttribute {
    val residentialAttribute = ResidentialAttribute(location = this, attributeType = attribute.type, attributeValue = attribute)
    if (attributes.add(residentialAttribute)) {
      userOrSystemInContext?.let { addHistory(LocationAttribute.ATTRIBUTES, null, attribute.description, userOrSystemInContext, LocalDateTime.now(clock)) }
    }
    return residentialAttribute
  }

  fun addAttributes(attributes: Set<ResidentialAttributeValue>): Set<ResidentialAttribute> {
    return attributes.map { addAttribute(it) }.toSet()
  }

  fun addSpecialistCellType(specialistCellType: SpecialistCellType, userOrSystemInContext: String? = null, clock: Clock? = null): SpecialistCell {
    val specialistCell = SpecialistCell(location = this, specialistCellType = specialistCellType)
    if (this.specialistCellTypes.add(specialistCell)) {
      userOrSystemInContext?.let { addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, null, specialistCellType.description, userOrSystemInContext, LocalDateTime.now(clock)) }
    }
    return specialistCell
  }

  fun addUsedFor(usedForType: UsedForType, userOrSystemInContext: String, clock: Clock): CellUsedFor {
    val cellUsedFor = CellUsedFor(location = this, usedFor = usedForType)
    if (this.usedFor.add(cellUsedFor)) {
      addHistory(LocationAttribute.USED_FOR, null, usedForType.description, userOrSystemInContext, LocalDateTime.now(clock))
    }
    return cellUsedFor
  }

  private fun removeUsedFor(typeToRemove: UsedForType = UsedForType.STANDARD_ACCOMMODATION, userOrSystemInContext: String, clock: Clock) {
    val usedForToRemove = usedFor.find { it.usedFor == typeToRemove }
    if (usedForToRemove != null) {
      addHistory(LocationAttribute.USED_FOR, typeToRemove.description, null, userOrSystemInContext, LocalDateTime.now(clock))
      usedFor.remove(usedForToRemove)
    }
  }

  override fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock): Cell {
    super.update(upsert, userOrSystemInContext, clock)

    if (upsert is PatchResidentialLocationRequest) {
      setAccommodationTypeForCell(upsert.accommodationType ?: this.accommodationType, userOrSystemInContext, clock)

      if (upsert.usedFor != null) {
        updateUsedFor(upsert.usedFor, userOrSystemInContext, clock)
      }
    }

    return this
  }

  fun updateSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    recordRemovedSpecialistCellTypes(specialistCellTypes, userOrSystemInContext, clock)
    this.specialistCellTypes.retainAll(
      specialistCellTypes.map {
        addSpecialistCellType(
          it,
          userOrSystemInContext,
          clock,
        )
      }.toSet(),
    )
  }

  fun updateUsedFor(
    usedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    recordRemovedUsedForTypes(usedFor, userOrSystemInContext, clock)
    this.usedFor.retainAll(usedFor.map { addUsedFor(it, userOrSystemInContext, clock) }.toSet())
  }

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock): Cell {
    super.sync(upsert, clock)

    setAccommodationTypeForCell(residentialHousingType.mapToAccommodationType(), upsert.lastUpdatedBy, clock)
    handleNomisCapacitySync(upsert, upsert.lastUpdatedBy, clock)

    if (upsert.attributes != null) {
      recordRemovedAttributes(upsert.attributes, upsert.lastUpdatedBy, clock)
      attributes.retainAll(upsert.attributes.map { addAttribute(it, upsert.lastUpdatedBy, clock) }.toSet())
    }

    return this
  }

  private fun setAccommodationTypeForCell(accommodationType: AccommodationType, userOrSystemInContext: String, clock: Clock) {
    addHistory(
      LocationAttribute.ACCOMMODATION_TYPE,
      this.accommodationType.description,
      accommodationType.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
    )
    this.accommodationType = accommodationType

    if (this.accommodationType == AccommodationType.NORMAL_ACCOMMODATION) {
      addUsedFor(UsedForType.STANDARD_ACCOMMODATION, userOrSystemInContext, clock)
    } else {
      removeUsedFor(userOrSystemInContext = userOrSystemInContext, clock = clock)
    }
  }

  private fun handleNomisCapacitySync(
    upsert: NomisSyncLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    upsert.capacity?.let {
      with(upsert.capacity) {
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

        if (capacity != null) {
          capacity?.setCapacity(maxCapacity, workingCapacity)
        } else {
          capacity = Capacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity)
        }
      }
    }

    upsert.certification?.let {
      with(it) {
        val oldCertification = if (certified) {
          "Certified"
        } else {
          "Uncertified"
        }
        addHistory(
          LocationAttribute.CERTIFIED_CAPACITY,
          certification?.capacityOfCertifiedCell?.toString(),
          capacityOfCertifiedCell.toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
        )
        if (certification != null) {
          certification?.setCertification(certified, capacityOfCertifiedCell)
        } else {
          certification = Certification(certified = certified, capacityOfCertifiedCell = capacityOfCertifiedCell)
        }

        addHistory(
          LocationAttribute.CERTIFIED,
          oldCertification,
          getCertifiedSummary(certification),
          userOrSystemInContext,
          LocalDateTime.now(clock),
        )
      }
    }
  }

  private fun recordRemovedAttributes(
    attributes: Set<ResidentialAttributeValue>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldAttributes = this.attributes.map { it.attributeValue }.toSet()
    oldAttributes.subtract(attributes).forEach { removedAttribute ->
      addHistory(LocationAttribute.ATTRIBUTES, removedAttribute.description, null, userOrSystemInContext, LocalDateTime.now(clock))
    }
  }

  private fun recordRemovedSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldSpecialistCellTypes = this.specialistCellTypes.map { it.specialistCellType }.toSet()
    oldSpecialistCellTypes.subtract(specialistCellTypes).forEach { removedSpecialistCellType ->
      addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, removedSpecialistCellType.description, null, userOrSystemInContext, LocalDateTime.now(clock))
    }
  }

  private fun recordRemovedUsedForTypes(
    usedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    val oldUsedFor = this.usedFor.map { it.usedFor }.toSet()
    oldUsedFor.subtract(usedFor).forEach { removedUsedFor ->
      addHistory(LocationAttribute.USED_FOR, removedUsedFor.description, null, userOrSystemInContext, LocalDateTime.now(clock))
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
  ): LocationDto =
    super.toDto(
      includeChildren = includeChildren,
      includeParent = includeParent,
      includeHistory = includeHistory,
      countInactiveCells = countInactiveCells,
      includeNonResidential = includeNonResidential,
      useHistoryForUpdate = useHistoryForUpdate,
      countCells = countCells,
    ).copy(
      oldWorkingCapacity = if (isTemporarilyDeactivated()) {
        getWorkingCapacity()
      } else {
        null
      },
      convertedCellType = convertedCellType,
      otherConvertedCellType = otherConvertedCellType,
    )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation =
    super.toLegacyDto(includeHistory = includeHistory).copy(
      ignoreWorkingCapacity = false,
      capacity = capacity?.toDto(),
      certification = certification?.toDto(),
    )
}
