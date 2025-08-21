package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PendingChangeDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ResidentialStructuralType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeUnlockedWhenNotLockedException
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
  prisonConfiguration: PrisonConfiguration? = null,
  status: LocationStatus,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = 1,
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

  private var locked: Boolean = false,

  private var residentialStructure: String? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  private val approvalRequests: MutableList<CertificationApprovalRequest> = mutableListOf(),

) : Location(
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

  fun findTopLevelResidentialLocation(): ResidentialLocation = (getParent() as? ResidentialLocation)?.findTopLevelResidentialLocation() ?: this

  override fun isResidentialRoomOrConvertedCell() = isNonResType() || isConvertedCell()

  override fun hasDeactivatedParent() = if (!isResidentialRoomOrConvertedCell()) {
    findDeactivatedParent() != null
  } else {
    false
  }

  fun isNonResType() = locationType in ResidentialLocationType.entries.filter { it.nonResType }.map { it.baseType }
  fun isLocationShownOnResidentialSummary() = locationType in ResidentialLocationType.entries.filter { it.display }.map { it.baseType }

  fun getWorkingCapacityIgnoreParent(): Int = cellLocations().filter { it.isActive() }
    .sumOf { it.getWorkingCapacity() ?: 0 }

  private fun getWorkingCapacityIgnoringInactiveStatus(): Int = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .sumOf { it.getWorkingCapacity() ?: 0 }

  fun calcWorkingCapacity(includeDraft: Boolean = false): Int = cellLocations().filter { it.isActiveAndAllParentsActive() || (includeDraft && it.isDraft()) }
    .sumOf { it.getWorkingCapacity() ?: 0 }

  fun calcMaxCapacity(includePendingOrDraft: Boolean = false): Int = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) && (!it.isDraft() || includePendingOrDraft) }
    .sumOf { it.getMaxCapacity(includePendingOrDraft) ?: 0 }

  private fun calcCertifiedNormalAccommodation(includePendingOrDraft: Boolean = false): Int = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) && (!it.isDraft() || includePendingOrDraft) }
    .sumOf { it.getCertifiedNormalAccommodation(includePendingOrDraft) ?: 0 }

  private fun hasCertifiedCells(): Boolean = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .any { it.isCertified() }

  override fun isLocationLocked() = locked

  private fun lockLocation() {
    this.locked = true
  }

  private fun unlockLocation() {
    this.locked = false
  }

  private fun hasPendingChangesBelowThisLevel() = childLocations.filterIsInstance<Cell>().any { it.hasPendingChanges() }

  private fun lock(lockTime: LocalDateTime, lockingUser: String, linkedTransaction: LinkedTransaction) {
    val oldStatus = getDerivedStatus(ignoreParentStatus = true).description
    lockLocation()
    addHistory(
      LocationAttribute.STATUS,
      oldStatus,
      getDerivedStatus(ignoreParentStatus = true).description,
      lockingUser,
      lockTime,
      linkedTransaction,
    )
    updatedBy = lockingUser
    whenUpdated = lockTime
  }

  private fun unlock(unlockTime: LocalDateTime, unlockingUser: String, linkedTransaction: LinkedTransaction) {
    val oldStatus = getDerivedStatus(ignoreParentStatus = true).description
    unlockLocation()
    addHistory(
      LocationAttribute.STATUS,
      oldStatus,
      getDerivedStatus(ignoreParentStatus = true).description,
      unlockingUser,
      unlockTime,
      linkedTransaction,
    )
    updatedBy = unlockingUser
    whenUpdated = unlockTime
  }

  fun setStructure(wingStructure: List<ResidentialStructuralType>) {
    this.residentialStructure = wingStructure.joinToString(separator = ",") { it.name }
  }

  fun getStructure(): List<ResidentialStructuralType>? = this.residentialStructure?.split(",")?.map { ResidentialStructuralType.valueOf(it.trim()) }

  fun getNextLevelTypeWithinStructure() = findTopLevelResidentialLocation().getStructure()?.let { structure ->
    val pos = structure.indexOfFirst { it.locationType == locationType } + 1
    if (pos < structure.size) structure[pos] else ResidentialStructuralType.valueOf(locationType.name).defaultNextLevel
  }

  fun requestApproval(requestedDate: LocalDateTime, requestedBy: String, linkedTransaction: LinkedTransaction): CertificationApprovalRequest {
    fun traverseAndLock(location: ResidentialLocation) {
      location.lock(requestedDate, requestedBy, linkedTransaction)
      location.childLocations.filterIsInstance<ResidentialLocation>().forEach { traverseAndLock(it) }
    }
    traverseAndLock(this)
    val approvalRequest = CertificationApprovalRequest(
      location = this,
      prisonId = this.prisonId,
      locationKey = this.getKey(),
      requestedBy = requestedBy,
      requestedDate = requestedDate,
      maxCapacityChange = calcMaxCapacity(true) - calcMaxCapacity(),
      workingCapacityChange = calcWorkingCapacity(true) - calcWorkingCapacity(),
      certifiedNormalAccommodationChange = calcCertifiedNormalAccommodation(true) - calcCertifiedNormalAccommodation(),
      locations = sortedSetOf(toCertificationApprovalRequestLocation()),
    ).apply {
      linkPendingChangesToApprovalRequest(approvalRequest = this)
    }

    approvalRequests.add(approvalRequest)
    return approvalRequest
  }

  private fun traverseAndUnlock(
    unlockedDate: LocalDateTime,
    unlockedBy: String,
    linkedTransaction: LinkedTransaction,
  ) {
    if (isLocationLocked()) {
      unlock(unlockedDate, unlockedBy, linkedTransaction)
      childLocations.filterIsInstance<ResidentialLocation>().forEach { it.traverseAndUnlock(unlockedDate, unlockedBy, linkedTransaction) }
    } else {
      throw LocationCannotBeUnlockedWhenNotLockedException(getKey())
    }
  }

  open fun linkPendingChangesToApprovalRequest(approvalRequest: CertificationApprovalRequest) {
    childLocations.filterIsInstance<ResidentialLocation>().forEach { it.linkPendingChangesToApprovalRequest(approvalRequest = approvalRequest) }
  }

  open fun applyPendingChanges(
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    childLocations.filterIsInstance<ResidentialLocation>().forEach { it.applyPendingChanges(approvedDate = approvedDate, approvedBy = approvedBy, linkedTransaction = linkedTransaction) }
  }

  open fun clearPendingChanges() {
    childLocations.filterIsInstance<ResidentialLocation>().forEach { it.clearPendingChanges() }
  }

  fun approve(
    approvedDate: LocalDateTime,
    approvedBy: String,
    linkedTransaction: LinkedTransaction,
  ) {
    traverseAndUnlock(approvedDate, approvedBy, linkedTransaction)
    applyPendingChanges(approvedDate = approvedDate, approvedBy = approvedBy, linkedTransaction = linkedTransaction)

    if (isDraft()) {
      temporarilyDeactivate(
        deactivationReasonDescription = "Approved pending activation",
        deactivatedReason = DeactivatedReason.OTHER,
        deactivatedDate = approvedDate,
        linkedTransaction = linkedTransaction,
        userOrSystemInContext = approvedBy,
      )
    }
  }

  fun reject(
    rejectedDate: LocalDateTime,
    rejectedBy: String,
    linkedTransaction: LinkedTransaction,
  ) {
    traverseAndUnlock(rejectedDate, rejectedBy, linkedTransaction)
    clearPendingChanges()
  }

  private fun getAttributes(): Set<ResidentialAttribute> = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .flatMap { it.attributes }
    .toSet()

  private fun getAccommodationTypes(): Set<AccommodationType> = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .map { it.accommodationType }
    .toSet()

  open fun getUsedForValues(): MutableSet<CellUsedFor> = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .flatMap { it.usedFor }
    .toMutableSet()

  private fun getSpecialistCellTypes(): Set<SpecialistCell> = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .flatMap { it.specialistCellTypes }
    .toSet()

  private fun isCurrentCellOrNotPermanentlyInactive(cell: Cell) = !cell.isPermanentlyDeactivated() || cell == this

  private fun getInactiveCellCount() = cellLocations().count { it.isTemporarilyDeactivated() }

  fun updateCellUsedFor(
    newUsedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    if (cellLocations().isNotEmpty()) {
      updateUsedFor(newUsedFor, userOrSystemInContext, clock, linkedTransaction)
    }
    findSubLocations().filterIsInstance<ResidentialLocation>().filter { it.cellLocations().isNotEmpty() }
      .forEach { it.updateUsedFor(newUsedFor, userOrSystemInContext, clock, linkedTransaction) }
  }

  open fun addUsedFor(
    usedForType: UsedForType,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ): CellUsedFor {
    addHistory(
      LocationAttribute.USED_FOR,
      null,
      usedForType.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    return CellUsedFor(location = this, usedFor = usedForType)
  }

  fun updateUsedFor(
    newUsedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    recordRemovedUsedForTypes(getUsedForValues(), newUsedFor, userOrSystemInContext, clock, linkedTransaction)
    getUsedForValues().retainAll(
      newUsedFor.map { addUsedFor(it, userOrSystemInContext, clock, linkedTransaction) }
        .toSet(),
    )
  }

  protected fun recordRemovedUsedForTypes(
    currentUsedFor: Set<CellUsedFor>,
    newUsedFor: Set<UsedForType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    val oldUsedFor = currentUsedFor.map { it.usedFor }.toSet()
    if (oldUsedFor != newUsedFor) {
      oldUsedFor.forEach { removedUsedFor ->
        addHistory(
          LocationAttribute.USED_FOR,
          removedUsedFor.description,
          null,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }

      oldUsedFor.intersect(newUsedFor).forEach { keptUsedFor ->
        addHistory(
          LocationAttribute.USED_FOR,
          null,
          keptUsedFor.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
  }

  fun updateCellSpecialistCellTypes(
    specialistCellTypes: Set<SpecialistCellType>,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    cellLocations().forEach {
      it.updateSpecialistCellTypes(
        specialistCellTypes,
        userOrSystemInContext,
        clock,
        linkedTransaction,
      )
    }
  }

  protected fun handleNomisCapacitySync(
    upsert: NomisSyncLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    upsert.capacity?.let {
      with(upsert.capacity) {
        addHistory(
          LocationAttribute.MAX_CAPACITY,
          capacity?.maxCapacity?.toString() ?: "None",
          maxCapacity.toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
        addHistory(
          LocationAttribute.WORKING_CAPACITY,
          capacity?.workingCapacity?.toString() ?: "None",
          workingCapacity.toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )

        if (capacity != null) {
          capacity?.setCapacity(maxCapacity, workingCapacity)
        } else {
          capacity = Capacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity)
        }
      }
    }
  }

  override fun sync(
    upsert: NomisSyncLocationRequest,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ): ResidentialLocation {
    super.sync(upsert, clock, linkedTransaction)

    addHistory(
      LocationAttribute.RESIDENTIAL_HOUSING_TYPE,
      this.residentialHousingType.description,
      upsert.residentialHousingType?.description,
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    upsert.residentialHousingType?.let {
      this.residentialHousingType = it
    }

    return this
  }

  fun toCellCertificateLocation(approvedLocation: ResidentialLocation): CellCertificateLocation {
    val subLocations: List<CellCertificateLocation> = childLocations
      .filterIsInstance<ResidentialLocation>()
      .filter { !it.isDraft() && (it.isStructural() || it.isCell() || it.isConvertedCell()) }
      .map { it.toCellCertificateLocation(approvedLocation) }

    val approvedLocationIsPartOfHierarchy = isInHierarchy(approvedLocation)
    return CellCertificateLocation(
      locationType = locationType,
      locationCode = getCode(),
      pathHierarchy = getPathHierarchy(),
      localName = localName?.capitalizeWords(),
      cellMark = if (this is Cell) {
        cellMark
      } else {
        null
      },
      level = getLevel(),
      inCellSanitation = if (this is Cell) {
        inCellSanitation
      } else {
        null
      },
      maxCapacity = calcMaxCapacity(),
      workingCapacity = if (approvedLocationIsPartOfHierarchy) {
        getWorkingCapacityIgnoringInactiveStatus()
      } else {
        calcWorkingCapacity()
      },
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
      specialistCellTypes = getSpecialistCellTypes().map { it.specialistCellType }.takeIf { it.isNotEmpty() }?.joinToString(separator = ",") { it.name },
      convertedCellType = if (this is Cell && isConvertedCell()) {
        convertedCellType
      } else {
        null
      },
      subLocations = subLocations.toSortedSet(),
    )
  }

  private fun toCertificationApprovalRequestLocation(): CertificationApprovalRequestLocation {
    val subLocations: List<CertificationApprovalRequestLocation> = childLocations
      .filterIsInstance<ResidentialLocation>()
      .filter { (it.isStructural() || it.isCell() || it.isConvertedCell()) }
      .map { it.toCertificationApprovalRequestLocation() }

    return CertificationApprovalRequestLocation(
      locationType = locationType,
      locationCode = getCode(),
      pathHierarchy = getPathHierarchy(),
      localName = localName?.capitalizeWords(),
      cellMark = if (this is Cell) {
        cellMark
      } else {
        null
      },
      level = getLevel(),
      inCellSanitation = if (this is Cell) {
        inCellSanitation
      } else {
        null
      },
      maxCapacity = calcMaxCapacity(true),
      workingCapacity = calcWorkingCapacity(true),
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(true),
      specialistCellTypes = getSpecialistCellTypes().map { it.specialistCellType }.takeIf { it.isNotEmpty() }?.joinToString(separator = ",") { it.name },
      convertedCellType = if (this is Cell && isConvertedCell()) {
        convertedCellType
      } else {
        null
      },
      subLocations = subLocations.toSortedSet(),
    )
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

    wingStructure = getStructure(),

    capacity = CapacityDto(
      maxCapacity = calcMaxCapacity(),
      workingCapacity = calcWorkingCapacity(),
    ),

    pendingChanges = if (hasPendingChanges() || hasPendingChangesBelowThisLevel()) {
      PendingChangeDto(
        maxCapacity = calcMaxCapacity(true),
        workingCapacity = calcWorkingCapacity(true),
        certifiedNormalAccommodation = calcCertifiedNormalAccommodation(true),
      )
    } else {
      null
    },

    certification = CertificationDto(
      certified = hasCertifiedCells(),
      capacityOfCertifiedCell = calcCertifiedNormalAccommodation(),
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
    ),

    accommodationTypes = getAccommodationTypes().map { it }.distinct().sortedBy { it.sequence },
    usedFor = getUsedForValues().map { it.usedFor }.distinct().sortedBy { it.sequence },

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

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation = super.toLegacyDto(includeHistory = includeHistory).copy(
    residentialHousingType = residentialHousingType,

    ignoreWorkingCapacity = true,
    capacity = CapacityDto(
      maxCapacity = calcMaxCapacity(),
      workingCapacity = calcWorkingCapacity(),
    ),

    certification = CertificationDto(
      certified = hasCertifiedCells(),
      capacityOfCertifiedCell = calcCertifiedNormalAccommodation(),
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
    ),

    attributes = getAttributes().map { it.attributeValue }.distinct().sortedBy { it.name },
  )

  override fun update(
    upsert: PatchLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ): ResidentialLocation {
    super.update(upsert, userOrSystemInContext, clock, linkedTransaction)

    if (upsert is PatchResidentialLocationRequest) {
      upsert.locationType?.let {
        this.locationType = it.baseType

        addHistory(
          LocationAttribute.LOCATION_TYPE,
          this.locationType.description,
          it.description,
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }

    return this
  }

  override fun toResidentialPrisonerLocation(mapOfPrisoners: Map<String, List<Prisoner>>): ResidentialPrisonerLocation = super.toResidentialPrisonerLocation(mapOfPrisoners).copy(
    capacity = CapacityDto(
      maxCapacity = calcMaxCapacity(),
      workingCapacity = calcWorkingCapacity(),
    ),
    certified = hasCertifiedCells(),
  )

  open fun setCapacity(
    maxCapacity: Int = 0,
    workingCapacity: Int = 0,
    userOrSystemInContext: String,
    amendedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
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
        LocationAttribute.MAX_CAPACITY,
        capacity?.maxCapacity?.toString() ?: "None",
        maxCapacity.toString(),
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.WORKING_CAPACITY,
        capacity?.workingCapacity?.let { calcWorkingCapacity().toString() } ?: "None",
        workingCapacity.toString(),
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )

      log.info("${getKey()}: Updating max capacity from ${capacity?.maxCapacity ?: 0} to $maxCapacity and working capacity from ${capacity?.workingCapacity ?: 0} to $workingCapacity")
      if (capacity != null) {
        capacity?.setCapacity(maxCapacity, workingCapacity)
      } else {
        capacity = Capacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity)
      }

      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate
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

  fun mapToAccommodationType(): AccommodationType = when (this) {
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
