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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
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
  status: LocationStatus,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = null,
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
  capacity: Capacity? = null,

  private var cellMark: String? = null,

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

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  var pendingChange: PendingLocationChange? = null,

  var inCellSanitation: Boolean? = null,

) : ResidentialLocation(
  id = id,
  code = code,
  pathHierarchy = pathHierarchy,
  locationType = locationType,
  prisonId = prisonId,
  status = status,
  parent = parent,
  localName = localName,
  comments = comments,
  orderWithinParentLocation = orderWithinParentLocation,
  deactivatedDate = deactivatedDate,
  deactivatedReason = deactivatedReason,
  proposedReactivationDate = proposedReactivationDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  createdBy = createdBy,
  residentialHousingType = residentialHousingType,
  capacity = capacity,
) {

  fun getWorkingCapacity() = capacity?.workingCapacity

  fun getMaxCapacity(includePendingChange: Boolean = false) = if (includePendingChange) {
    pendingChange?.let { it.capacity?.maxCapacity } ?: capacity?.maxCapacity
  } else {
    capacity?.maxCapacity
  }

  fun getCapacityOfCertifiedCell() = certification?.capacityOfCertifiedCell

  fun setCapacityOfCertifiedCell(capacityOfCertifiedCell: Int, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): Boolean {
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      capacityOfCertifiedCell.toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
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

  override fun isCell() = !isConvertedCell()

  override fun isConvertedCell() = convertedCellType != null

  private fun getConvertedCellTypeSummary() = listOfNotBlank(convertedCellType?.description, otherConvertedCellType).joinToString(" - ")

  fun convertToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.STATUS,
      this.getDerivedStatus().description,
      DerivedLocationStatus.NON_RESIDENTIAL.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    updateNonResidentialCellType(convertedCellType, otherConvertedCellType, userOrSystemInContext, clock, linkedTransaction)
    addHistory(
      LocationAttribute.LOCATION_TYPE,
      locationType.name,
      getDerivedLocationType().name,
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )

    addHistory(
      LocationAttribute.MAX_CAPACITY,
      capacity?.maxCapacity?.toString() ?: "None",
      "None",
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    addHistory(
      LocationAttribute.WORKING_CAPACITY,
      capacity?.workingCapacity?.toString() ?: "None",
      "None",
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )

    capacity = null
    deCertifyCell(userOrSystemInContext, clock, linkedTransaction)
    recordRemovedSpecialistCellTypes(specialistCellTypes.map { it.specialistCellType }.toSet(), userOrSystemInContext, clock, linkedTransaction)
    specialistCellTypes.clear()

    recordRemovedUsedForTypes(
      currentUsedFor = this.usedFor,
      newUsedFor = this.usedFor.map { it.usedFor }.toSet(),
      userOrSystemInContext = userOrSystemInContext,
      clock = clock,
      linkedTransaction = linkedTransaction,
    )
    this.usedFor.clear()
  }

  override fun getUsedForValues() = this.usedFor

  fun updateNonResidentialCellType(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.CONVERTED_CELL_TYPE,
      this.getConvertedCellTypeSummary(),
      listOfNotBlank(convertedCellType.description, otherConvertedCellType).joinToString(" - "),
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
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

  fun convertToCell(accommodationType: AllowedAccommodationTypeForConversion, usedForTypes: List<UsedForType>? = null, specialistCellTypes: Set<SpecialistCellType>? = null, maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    val amendedDate = LocalDateTime.now(clock)
    addHistory(
      LocationAttribute.STATUS,
      this.getDerivedStatus().description,
      LocationStatus.ACTIVE.description,
      userOrSystemInContext,
      amendedDate,
      linkedTransaction,
    )
    convertedCellType = null
    otherConvertedCellType = null
    certifyCell(userOrSystemInContext, clock, linkedTransaction)

    setAccommodationTypeForCell(accommodationType.mapsTo, userOrSystemInContext, clock, linkedTransaction)

    usedForTypes?.forEach {
      addUsedFor(it, userOrSystemInContext, clock, linkedTransaction)
    }

    specialistCellTypes?.let { updateSpecialistCellTypes(specialistCellTypes = it, clock = clock, userOrSystemInContext = userOrSystemInContext, linkedTransaction = linkedTransaction) }

    setCapacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity, userOrSystemInContext = userOrSystemInContext, amendedDate = amendedDate, linkedTransaction = linkedTransaction)
    if (hasDeactivatedParent()) {
      reactivate(userOrSystemInContext, clock, linkedTransaction)
    }
    this.residentialHousingType = this.accommodationType.mapToResidentialHousingType()
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = amendedDate
  }

  override fun setCapacity(maxCapacity: Int, workingCapacity: Int, userOrSystemInContext: String, amendedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    if (!(isPermanentlyDeactivated() || isTemporarilyDeactivated()) && workingCapacity == 0 && accommodationType == AccommodationType.NORMAL_ACCOMMODATION && specialistCellTypes.isEmpty()) {
      throw CapacityException(
        getKey(),
        "Cannot have a 0 working capacity with normal accommodation and not specialist cell",
        ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
      )
    }
    if (isCertificationApprovalProcessRequired() && getMaxCapacity(includePendingChange = true) != maxCapacity) {
      if (pendingChange == null) {
        pendingChange = PendingLocationChange()
      }
      pendingChange?.let { it.capacity = Capacity(maxCapacity = maxCapacity, workingCapacity = getWorkingCapacity() ?: 0) }
    } else {
      super.setCapacity(maxCapacity, workingCapacity, userOrSystemInContext, amendedDate, linkedTransaction)
    }
  }

  fun certifyCell(userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    val oldCertification = getCertifiedSummary(this.certification)
    if (certification != null) {
      certification?.setCertification(true, certification?.capacityOfCertifiedCell ?: 0)
    } else {
      certification = Certification(certified = true, capacityOfCertifiedCell = 0)
    }

    addHistory(
      LocationAttribute.CERTIFICATION,
      oldCertification,
      getCertifiedSummary(this.certification),
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun deCertifyCell(userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.CERTIFICATION,
      getCertifiedSummary(this.certification),
      "Uncertified",
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    addHistory(
      LocationAttribute.CERTIFIED_CAPACITY,
      certification?.capacityOfCertifiedCell?.toString(),
      (certification?.capacityOfCertifiedCell ?: 0).toString(),
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )

    if (certification != null) {
      certification?.setCertification(false, certification?.capacityOfCertifiedCell ?: 0)
    } else {
      certification = Certification(certified = false, capacityOfCertifiedCell = 0)
    }

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun addAttribute(attribute: ResidentialAttributeValue, linkedTransaction: LinkedTransaction? = null, userOrSystemInContext: String? = null, clock: Clock? = null): ResidentialAttribute {
    val residentialAttribute = ResidentialAttribute(location = this, attributeType = attribute.type, attributeValue = attribute)
    if (attributes.add(residentialAttribute)) {
      if (userOrSystemInContext != null && linkedTransaction != null && clock != null) {
        addHistory(LocationAttribute.ATTRIBUTES, null, attribute.description, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
      }
    }
    return residentialAttribute
  }

  fun addAttributes(attributes: Set<ResidentialAttributeValue>): Set<ResidentialAttribute> = attributes.map { addAttribute(it) }.toSet()

  fun addSpecialistCellType(specialistCellType: SpecialistCellType, linkedTransaction: LinkedTransaction? = null, userOrSystemInContext: String? = null, clock: Clock? = null): SpecialistCell {
    val specialistCell = SpecialistCell(location = this, specialistCellType = specialistCellType)
    if (this.specialistCellTypes.add(specialistCell)) {
      userOrSystemInContext?.let { addHistory(LocationAttribute.SPECIALIST_CELL_TYPE, null, specialistCellType.description, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction!!) }
    }
    return specialistCell
  }

  override fun addUsedFor(usedForType: UsedForType, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): CellUsedFor {
    val cellUsedFor = CellUsedFor(location = this, usedFor = usedForType)
    if (this.usedFor.add(cellUsedFor)) {
      addHistory(LocationAttribute.USED_FOR, null, usedForType.description, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
    }
    return cellUsedFor
  }

  private fun removeUsedFor(typeToRemove: UsedForType = UsedForType.STANDARD_ACCOMMODATION, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    val usedForToRemove = usedFor.find { it.usedFor == typeToRemove }
    if (usedForToRemove != null) {
      addHistory(LocationAttribute.USED_FOR, typeToRemove.description, null, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
      usedFor.remove(usedForToRemove)
    }
  }

  override fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): Cell {
    super.update(upsert, userOrSystemInContext, clock, linkedTransaction)

    if (upsert is PatchResidentialLocationRequest) {
      setAccommodationTypeForCell(upsert.accommodationType ?: this.accommodationType, userOrSystemInContext, clock, linkedTransaction)

      if (upsert.usedFor != null) {
        updateUsedFor(upsert.usedFor, userOrSystemInContext, clock, linkedTransaction)
      }
    }

    return this
  }

  fun updateSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    recordRemovedSpecialistCellTypes(specialistCellTypes, userOrSystemInContext, clock, linkedTransaction)
    this.specialistCellTypes.retainAll(
      specialistCellTypes.map {
        addSpecialistCellType(
          it,
          linkedTransaction,
          userOrSystemInContext,
          clock,
        )
      }.toSet(),
    )
  }

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): Cell {
    super.sync(upsert, clock, linkedTransaction)

    setAccommodationTypeForCell(residentialHousingType.mapToAccommodationType(), upsert.lastUpdatedBy, clock, linkedTransaction)
    handleNomisCapacitySync(upsert, upsert.lastUpdatedBy, clock, linkedTransaction)
    handleNomisCertSync(upsert, upsert.lastUpdatedBy, clock, linkedTransaction)

    if (upsert.attributes != null) {
      recordRemovedAttributes(upsert.attributes, upsert.lastUpdatedBy, clock, linkedTransaction)
      attributes.retainAll(upsert.attributes.map { addAttribute(it, userOrSystemInContext = upsert.lastUpdatedBy, clock = clock, linkedTransaction = linkedTransaction) }.toSet())
    }

    return this
  }

  private fun setAccommodationTypeForCell(accommodationType: AccommodationType, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.ACCOMMODATION_TYPE,
      this.accommodationType.description,
      accommodationType.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    this.accommodationType = accommodationType

    if (this.accommodationType == AccommodationType.NORMAL_ACCOMMODATION) {
      addUsedFor(UsedForType.STANDARD_ACCOMMODATION, userOrSystemInContext, clock, linkedTransaction)
    } else {
      removeUsedFor(userOrSystemInContext = userOrSystemInContext, clock = clock, linkedTransaction = linkedTransaction)
    }
  }

  private fun handleNomisCertSync(
    upsert: NomisSyncLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
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
          linkedTransaction,
        )
        if (certification != null) {
          certification?.setCertification(certified, capacityOfCertifiedCell)
        } else {
          certification = Certification(certified = certified, capacityOfCertifiedCell = capacityOfCertifiedCell)
        }

        addHistory(
          LocationAttribute.CERTIFICATION,
          oldCertification,
          getCertifiedSummary(certification),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
  }

  private fun recordRemovedAttributes(
    attributes: Set<ResidentialAttributeValue>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldAttributes = this.attributes.map { it.attributeValue }.toSet()
    oldAttributes.subtract(attributes).forEach { removedAttribute ->
      addHistory(LocationAttribute.ATTRIBUTES, removedAttribute.description, null, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
    }
  }

  private fun recordRemovedSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldSpecialistCellTypes = this.specialistCellTypes.map { it.specialistCellType }.toSet()
    if (oldSpecialistCellTypes != specialistCellTypes) {
      oldSpecialistCellTypes.forEach { removedSpecialistCellType ->
        addHistory(
          LocationAttribute.SPECIALIST_CELL_TYPE,
          removedSpecialistCellType.description,
          null,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }

      oldSpecialistCellTypes.intersect(specialistCellTypes).forEach { keptCellTypes ->
        addHistory(
          LocationAttribute.SPECIALIST_CELL_TYPE,
          null,
          keptCellTypes.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
  }

  override fun approve(
    approvedDate: LocalDateTime,
    approvedBy: String,
    linkedTransaction: LinkedTransaction,
    comments: String?,
  ) {
    super.approve(approvedDate, approvedBy, linkedTransaction, comments)

    pendingChange?.let { pending ->
      applyPendingChanges(pending, approvedBy, approvedDate, linkedTransaction)
    }
    pendingChange = null
  }

  override fun hasPendingChanges() = super.hasPendingChanges() || pendingChange != null

  private fun applyPendingChanges(
    pending: PendingLocationChange,
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    pending.capacity?.let { cap ->
      setCapacity(
        maxCapacity = cap.maxCapacity,
        workingCapacity = cap.workingCapacity,
        userOrSystemInContext = approvedBy,
        amendedDate = approvedDate,
        linkedTransaction = linkedTransaction,
      )
    }
  }

  override fun reject(
    rejectedDate: LocalDateTime,
    rejectedBy: String,
    linkedTransaction: LinkedTransaction,
    comments: String?,
  ) {
    super.reject(rejectedDate, rejectedBy, linkedTransaction, comments)
    pendingChange = null
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
  ): LocationDto = super.toDto(
    includeChildren = includeChildren,
    includeParent = includeParent,
    includeHistory = includeHistory,
    countInactiveCells = countInactiveCells,
    includeNonResidential = includeNonResidential,
    useHistoryForUpdate = useHistoryForUpdate,
    countCells = countCells,
    formatLocalName = formatLocalName,
  ).copy(
    oldWorkingCapacity = if (isTemporarilyDeactivated()) {
      getWorkingCapacity()
    } else {
      null
    },
    convertedCellType = convertedCellType,
    otherConvertedCellType = otherConvertedCellType,
    inCellSanitation = inCellSanitation,
    cellMark = cellMark,
  )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation = super.toLegacyDto(includeHistory = includeHistory).copy(
    ignoreWorkingCapacity = false,
    capacity = capacity?.toDto(),
    certification = certification?.toDto(),
  )
}
