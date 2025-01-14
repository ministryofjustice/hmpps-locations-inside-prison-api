package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToOne
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialPrisonerLocation
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
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,

  @Enumerated(EnumType.STRING)
  open var residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  open var capacity: Capacity? = null,

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

  override fun findDeactivatedLocationInHierarchy(): Location? {
    if (!isActive()) {
      return this
    }
    return if (!isResidentialRoomOrConvertedCell()) {
      findDeactivatedParent()
    } else {
      null
    }
  }

  override fun isResidentialRoomOrConvertedCell() = isNonResType() || isConvertedCell()

  override fun hasDeactivatedParent() = if (!isResidentialRoomOrConvertedCell()) {
    findDeactivatedParent() != null
  } else {
    false
  }

  fun isNonResType() = locationType in ResidentialLocationType.entries.filter { it.nonResType }.map { it.baseType }
  fun isLocationShownOnResidentialSummary() =
    locationType in ResidentialLocationType.entries.filter { it.display }.map { it.baseType }

  fun getWorkingCapacityIgnoreParent(): Int {
    return cellLocations().filter { it.isActive() }
      .sumOf { it.getWorkingCapacity() ?: 0 }
  }

  fun calcWorkingCapacity(): Int {
    return cellLocations().filter { it.isActiveAndAllParentsActive() }
      .sumOf { it.getWorkingCapacity() ?: 0 }
  }

  private fun getMaxCapacity(): Int {
    return cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
      .sumOf { it.getMaxCapacity() ?: 0 }
  }

  private fun getCapacityOfCertifiedCell(): Int {
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

  private fun getAccommodationTypes(): Set<AccommodationType> {
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

  private fun getInactiveCellCount() = cellLocations().count { it.isTemporarilyDeactivated() }

  fun updateCellUsedFor(setOfUsedFor: Set<UsedForType>, userOrSystemInContext: String, clock: Clock) {
    cellLocations().forEach { it.updateUsedFor(setOfUsedFor, userOrSystemInContext, clock) }
  }

  fun updateCellSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    cellLocations().forEach { it.updateSpecialistCellTypes(specialistCellTypes, userOrSystemInContext, clock) }
  }

  protected fun handleNomisCapacitySync(
    upsert: NomisSyncLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    upsert.capacity?.let {
      with(upsert.capacity) {
        addHistory(
          LocationAttribute.CAPACITY,
          capacity?.maxCapacity?.toString() ?: "None",
          maxCapacity.toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
        )
        addHistory(
          LocationAttribute.OPERATIONAL_CAPACITY,
          capacity?.workingCapacity?.toString() ?: "None",
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
  }

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock): ResidentialLocation {
    super.sync(upsert, clock)

    addHistory(
      LocationAttribute.RESIDENTIAL_HOUSING_TYPE,
      this.residentialHousingType.description,
      upsert.residentialHousingType?.description,
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
    )
    upsert.residentialHousingType?.let {
      this.residentialHousingType = it
    }

    return this
  }

  override fun toDto(
    includeChildren: Boolean,
    includeParent: Boolean,
    includeHistory: Boolean,
    countInactiveCells: Boolean,
    includeNonResidential: Boolean,
    useHistoryForUpdate: Boolean,
    countCells: Boolean,
    formatLocalName: Boolean,
  ): LocationDto {
    return super.toDto(
      includeChildren = includeChildren,
      includeParent = includeParent,
      includeHistory = includeHistory,
      countInactiveCells = countInactiveCells,
      includeNonResidential = includeNonResidential,
      useHistoryForUpdate = useHistoryForUpdate,
      countCells = countCells,
      formatLocalName = formatLocalName,
    ).copy(

      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = calcWorkingCapacity(),
      ),

      certification = CertificationDto(
        certified = hasCertifiedCells(),
        capacityOfCertifiedCell = getCapacityOfCertifiedCell(),
      ),

      accommodationTypes = getAccommodationTypes().map { it }.distinct().sortedBy { it.sequence },
      usedFor = getUsedFor().map { it.usedFor }.distinct().sortedBy { it.sequence },

      specialistCellTypes = getSpecialistCellTypes().map { it.specialistCellType }.distinct().sortedBy { it.sequence },
      inactiveCells = if (countInactiveCells) {
        getInactiveCellCount()
      } else {
        null
      },
      numberOfCellLocations = if (countCells) {
        countCellAndNonResLocations()
      } else {
        null
      },
    )
  }

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation {
    return super.toLegacyDto(includeHistory = includeHistory).copy(
      residentialHousingType = residentialHousingType,

      ignoreWorkingCapacity = true,
      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = calcWorkingCapacity(),
      ),

      certification = CertificationDto(
        certified = hasCertifiedCells(),
        capacityOfCertifiedCell = getCapacityOfCertifiedCell(),
      ),

      attributes = getAttributes().map { it.attributeValue }.distinct().sortedBy { it.name },
    )
  }

  override fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock): ResidentialLocation {
    super.update(upsert, userOrSystemInContext, clock)

    if (upsert is PatchResidentialLocationRequest) {
      upsert.locationType?.let {
        this.locationType = it.baseType

        addHistory(
          LocationAttribute.LOCATION_TYPE,
          this.locationType.description,
          it.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
        )
      }
    }

    return this
  }

  override fun toResidentialPrisonerLocation(mapOfPrisoners: Map<String, List<Prisoner>>): ResidentialPrisonerLocation =
    super.toResidentialPrisonerLocation(mapOfPrisoners).copy(
      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = calcWorkingCapacity(),
      ),
      certified = hasCertifiedCells(),
    )

  open fun setCapacity(maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock) {
    if (isCell() || isVirtualResidentialLocation()) {
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

      addHistory(
        LocationAttribute.CAPACITY,
        capacity?.maxCapacity?.toString() ?: "None",
        maxCapacity.toString(),
        userOrSystemInContext,
        LocalDateTime.now(clock),
      )
      addHistory(
        LocationAttribute.OPERATIONAL_CAPACITY,
        capacity?.workingCapacity?.let { calcWorkingCapacity().toString() } ?: "None",
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
      log.warn("Capacity cannot be set, not a cell or virtual location")
    }
  }
}

enum class ResidentialHousingType(
  val description: String,
  val sequence: Int = 99,
) {
  HEALTHCARE("Healthcare", 1),
  HOLDING_CELL("Holding Cell", 2),
  NORMAL_ACCOMMODATION("Normal Accommodation", 3),
  RECEPTION("Reception", 4),
  SEGREGATION("Segregation", 5),
  SPECIALIST_CELL("Specialist Cell", 6),
  OTHER_USE("Other Use", 99),
  ;

  fun mapToAccommodationType(): AccommodationType {
    return when (this) {
      NORMAL_ACCOMMODATION -> AccommodationType.NORMAL_ACCOMMODATION
      HEALTHCARE -> AccommodationType.HEALTHCARE_INPATIENTS
      SEGREGATION -> AccommodationType.CARE_AND_SEPARATION

      SPECIALIST_CELL,
      HOLDING_CELL,
      OTHER_USE,
      RECEPTION,
      -> AccommodationType.OTHER_NON_RESIDENTIAL
    }
  }
}
