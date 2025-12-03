package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.annotations.BatchSize
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalOnLocationCannotBeUpdatedException
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
  prisonConfiguration: PrisonConfiguration? = null,
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

  var cellMark: String? = null,

  @Column(nullable = false)
  var certifiedCell: Boolean = false,

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
  prisonConfiguration = prisonConfiguration,
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
    pendingChange?.maxCapacity ?: capacity?.maxCapacity
  } else {
    capacity?.maxCapacity
  }

  fun getCertifiedNormalAccommodation(includePendingChange: Boolean = false) = if (includePendingChange) {
    pendingChange?.certifiedNormalAccommodation ?: capacity?.certifiedNormalAccommodation
  } else {
    capacity?.certifiedNormalAccommodation
  }

  fun isCertified() = certifiedCell

  override fun getDerivedLocationType() = if (isConvertedCell()) {
    LocationType.ROOM
  } else {
    locationType
  }

  override fun isCell() = !isConvertedCell()

  override fun isConvertedCell() = convertedCellType != null

  private fun getConvertedCellTypeSummary() = listOfNotBlank(convertedCellType?.description, otherConvertedCellType).joinToString(" - ")

  fun convertToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (isLocationLocked()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }

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
    recordRemovedSpecialistCellTypes(getSpecialistCellTypesForCell(), userOrSystemInContext, clock, linkedTransaction)
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
    if (isLocationLocked()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
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
    certifiedCell = false
  }

  fun convertToCell(accommodationType: AllowedAccommodationTypeForConversion, usedForTypes: List<UsedForType>? = null, specialistCellTypes: Set<SpecialistCellType>? = null, maxCapacity: Int = 0, workingCapacity: Int = 0, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (isLocationLocked()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }

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
    certifyCell(userOrSystemInContext, amendedDate, linkedTransaction)

    setAccommodationTypeForCell(accommodationType.mapsTo, userOrSystemInContext, clock, linkedTransaction)

    usedForTypes?.forEach {
      addUsedFor(it, userOrSystemInContext, clock, linkedTransaction)
    }

    specialistCellTypes?.let { updateSpecialistCellTypes(specialistCellTypes = it, clock = clock, userOrSystemInContext = userOrSystemInContext, linkedTransaction = linkedTransaction) }

    setCapacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity, certifiedNormalAccommodation = workingCapacity, userOrSystemInContext = userOrSystemInContext, amendedDate = amendedDate, linkedTransaction = linkedTransaction)

    if (hasDeactivatedParent()) {
      reactivate(userOrSystemInContext, clock, linkedTransaction)
    }
    this.residentialHousingType = this.accommodationType.mapToResidentialHousingType()
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = amendedDate
  }

  override fun setCapacity(maxCapacity: Int, workingCapacity: Int, certifiedNormalAccommodation: Int, userOrSystemInContext: String, amendedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    validateCapacity(
      locationKey = getKey(),
      accommodationType = accommodationType,
      workingCapacity = workingCapacity,
      maxCapacity = maxCapacity,
      certifiedNormalAccommodation = certifiedNormalAccommodation,
      specialistCellTypes = getSpecialistCellTypesForCell(),
      temporarilyDeactivated = isTemporarilyDeactivated(),
      permanentlyDeactivated = isPermanentlyDeactivated(),
      virtualLocation = isVirtualResidentialLocation(),
    )

    if (isLocationLocked()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
    super.setCapacity(
      maxCapacity = maxCapacity,
      workingCapacity = workingCapacity,
      certifiedNormalAccommodation = certifiedNormalAccommodation,
      userOrSystemInContext = userOrSystemInContext,
      amendedDate = amendedDate,
      linkedTransaction = linkedTransaction,
    )
  }

  fun getCertifiedSummary() = if (certifiedCell) {
    "Certified"
  } else {
    "Uncertified"
  }

  fun certifyCell(
    cellUpdatedBy: String,
    updatedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldCertification = getCertifiedSummary()
    certifiedCell = true

    addHistory(
      LocationAttribute.CERTIFICATION,
      oldCertification,
      getCertifiedSummary(),
      cellUpdatedBy,
      updatedDate,
      linkedTransaction,
    )

    this.updatedBy = cellUpdatedBy
    this.whenUpdated = updatedDate
  }

  fun deCertifyCell(userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.CERTIFICATION,
      getCertifiedSummary(),
      "Uncertified",
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )

    certifiedCell = false

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
    if (isLocationLocked()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
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
    upsert.certifiedCell?.let { certifiedCell = it }
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

  @Deprecated("Seperate certification upsert not needed")
  private fun handleNomisCertSync(
    upsert: NomisSyncLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    upsert.certification?.let { cert ->
      with(cert) {
        addHistory(
          LocationAttribute.CERTIFIED_CAPACITY,
          capacity?.certifiedNormalAccommodation?.toString(),
          getCNA().toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )

        if (capacity != null) {
          capacity!!.certifiedNormalAccommodation = getCNA()
        } else {
          capacity = Capacity(certifiedNormalAccommodation = getCNA())
        }

        val oldCertification = getCertifiedSummary()
        certifiedCell = certified
        addHistory(
          LocationAttribute.CERTIFICATION,
          oldCertification,
          getCertifiedSummary(),
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
    val oldSpecialistCellTypes = getSpecialistCellTypesForCell()
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

  private fun getSpecialistCellTypesForCell(): Set<SpecialistCellType> = specialistCellTypes.map { it.specialistCellType }.toSet()

  override fun hasPendingChanges() = super.hasPendingChanges() || pendingChange != null

  override fun linkPendingChangesToApprovalRequest(approvalRequest: LocationCertificationApprovalRequest) {
    pendingChange?.let { it.approvalRequest = approvalRequest }
  }

  override fun applyPendingChanges(
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    pendingChange?.let { pc ->

      if (pc.maxCapacity != null || pc.certifiedNormalAccommodation != null) {
        setCapacity(
          maxCapacity = pc.maxCapacity ?: getMaxCapacity() ?: 0,
          workingCapacity = getWorkingCapacity() ?: 0,
          certifiedNormalAccommodation = pc.certifiedNormalAccommodation ?: getCertifiedNormalAccommodation() ?: 0,
          userOrSystemInContext = approvedBy,
          amendedDate = approvedDate,
          linkedTransaction = linkedTransaction,
        )
      }
    }

    if (isDraft()) {
      certifyCell(approvedBy, approvedDate, linkedTransaction)
    }

    clearPendingChanges()
  }

  override fun clearPendingChanges() {
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
    certifiedCell = certifiedCell,
    certification = Certification(
      certifiedNormalAccommodation = capacity?.certifiedNormalAccommodation,
      certified = certifiedCell,
    ),
  )
}
