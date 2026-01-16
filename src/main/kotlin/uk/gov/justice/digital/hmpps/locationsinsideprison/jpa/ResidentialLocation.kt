package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PendingChangeDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ResidentialStructuralType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequiredAboveThisLevelException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationDoesNotRequireApprovalException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
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
  status: LocationStatus,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = 1,
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: SortedSet<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,

  @Enumerated(EnumType.STRING)
  open var residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  open var capacity: Capacity? = null,

  private var residentialStructure: String? = null,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  private val approvalRequests: SortedSet<LocationCertificationApprovalRequest> = sortedSetOf(),

) : Location(
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

  fun calcCertifiedNormalAccommodation(includePendingOrDraft: Boolean = false): Int = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) && (!it.isDraft() || includePendingOrDraft) }
    .sumOf { it.getCertifiedNormalAccommodation(includePendingOrDraft) ?: 0 }

  private fun hasCertifiedCells(): Boolean = cellLocations().filter { isCurrentCellOrNotPermanentlyInactive(it) }
    .any { it.isCertified() }

  override fun hasPendingCertificationApproval() = getPendingApprovalRequest() != null

  private fun hasPendingChangesBelowThisLevel() = childLocations.filterIsInstance<Cell>().any { it.hasPendingCertificationApproval() || it.isDraft() }

  fun getPendingApprovalRequest(): LocationCertificationApprovalRequest? = findHighestLevelPending()?.approvalRequests?.firstOrNull { it.isPending() }

  protected fun findHighestLevelPending(includeDrafts: Boolean = false): ResidentialLocation? {
    var current: ResidentialLocation? = this
    var highestPending: ResidentialLocation? = null

    while (current != null) {
      if ((includeDrafts && current.isDraft()) || current.approvalRequests.firstOrNull { it.isPending() } != null) {
        highestPending = current
      }
      current = current.getParent() as? ResidentialLocation
    }

    return highestPending
  }

  fun setStructure(wingStructure: List<ResidentialStructuralType>) {
    this.residentialStructure = wingStructure.joinToString(separator = ",") { it.name }
  }

  fun getStructure(): List<ResidentialStructuralType>? = this.residentialStructure?.split(",")?.map { ResidentialStructuralType.valueOf(it.trim()) }

  fun getNextLevelTypeWithinStructure() = findTopLevelResidentialLocation().getStructure()?.let { structure ->
    val pos = structure.indexOfFirst { it.locationType == locationType } + 1
    if (pos < structure.size) structure[pos] else ResidentialStructuralType.valueOf(locationType.name).defaultNextLevel
  }

  fun getDefaultNextLevel() = getResidentialStructuralType()?.defaultNextLevel

  fun getResidentialStructuralType() = ResidentialStructuralType.entries.firstOrNull { it.locationType == locationType }

  fun requestApprovalForDraftLocation(
    requestedDate: LocalDateTime,
    requestedBy: String,
  ): LocationCertificationApprovalRequest {
    val topLevelPendingLocation = findHighestLevelPending(includeDrafts = true)
    if (topLevelPendingLocation != null && this != topLevelPendingLocation) {
      throw ApprovalRequiredAboveThisLevelException(this.getKey(), topLevelPendingLocation.getKey())
    }

    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }

    if (!isDraft()) {
      throw LocationDoesNotRequireApprovalException(getKey())
    }

    val approvalRequest = LocationCertificationApprovalRequest(
      approvalType = ApprovalType.DRAFT,
      location = this,
      prisonId = this.prisonId,
      locationKey = this.getKey(),
      requestedBy = requestedBy,
      requestedDate = requestedDate,
      maxCapacityChange = calcMaxCapacity(true) - calcMaxCapacity(),
      workingCapacityChange = calcWorkingCapacity(true) - calcWorkingCapacity(),
      certifiedNormalAccommodationChange = calcCertifiedNormalAccommodation(true) - calcCertifiedNormalAccommodation(),
      locations = sortedSetOf(toCertificationApprovalRequestLocation(true)),
    ).apply {
      linkPendingChangesToApprovalRequest(approvalRequest = this)
    }

    approvalRequests.add(approvalRequest)
    return approvalRequest
  }

  fun requestApprovalForDeactivation(
    requestedDate: LocalDateTime,
    requestedBy: String,
    workingCapacityChange: Int,
  ): LocationCertificationApprovalRequest {
    val topLevelPendingLocation = findHighestLevelPending(includeDrafts = true)
    if (topLevelPendingLocation != null && this != topLevelPendingLocation) {
      throw ApprovalRequiredAboveThisLevelException(this.getKey(), topLevelPendingLocation.getKey())
    }

    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }

    val approvalRequest = LocationCertificationApprovalRequest(
      approvalType = ApprovalType.DEACTIVATION,
      location = this,
      prisonId = this.prisonId,
      locationKey = this.getKey(),
      requestedBy = requestedBy,
      requestedDate = requestedDate,
      maxCapacityChange = 0,
      workingCapacityChange = workingCapacityChange,
      certifiedNormalAccommodationChange = 0,
      locations = sortedSetOf(toCertificationApprovalRequestLocation(false)),
    ).apply {
      linkPendingChangesToApprovalRequest(approvalRequest = this)
    }

    approvalRequests.add(approvalRequest)
    return approvalRequest
  }

  open fun linkPendingChangesToApprovalRequest(approvalRequest: LocationCertificationApprovalRequest) {
    getResidentialLocationsBelowThisLevel()
      .forEach { it.linkPendingChangesToApprovalRequest(approvalRequest = approvalRequest) }
  }

  open fun applyPendingChanges(
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    getResidentialLocationsBelowThisLevel().forEach {
      it.applyPendingChanges(
        approvedDate = approvedDate,
        approvedBy = approvedBy,
        linkedTransaction = linkedTransaction,
      )
    }
  }

  fun approve(
    approvedDate: LocalDateTime,
    approvedBy: String,
    linkedTransaction: LinkedTransaction,
  ) {
    applyPendingChanges(approvedDate = approvedDate, approvedBy = approvedBy, linkedTransaction = linkedTransaction)

    if (isDraft()) {
      temporarilyDeactivate(
        deactivationReasonDescription = "New location",
        deactivatedReason = DeactivatedReason.OTHER,
        deactivatedDate = approvedDate,
        linkedTransaction = linkedTransaction,
        userOrSystemInContext = approvedBy,
      )
    }
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

  fun removeCell(cell: Cell): Boolean {
    if (cell.isDraft()) {
      return childLocations.remove(cell)
    }
    return false
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
        val changeCNA = certifiedNormalAccommodation ?: capacity?.certifiedNormalAccommodation ?: 0

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

        addHistory(
          LocationAttribute.CERTIFIED_CAPACITY,
          capacity?.certifiedNormalAccommodation?.toString() ?: "None",
          changeCNA.toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )

        if (capacity != null) {
          capacity?.setCapacity(
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            certifiedNormalAccommodation = changeCNA,
          )
        } else {
          capacity = Capacity(
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            certifiedNormalAccommodation = changeCNA,
          )
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

  fun toCellCertificateLocation(approvalRequest: CertificationApprovalRequest): CellCertificateLocation {
    val subLocations: List<CellCertificateLocation> = getResidentialLocationsBelowThisLevel()
      .filter { !it.isDraft() && (it.isStructural() || it.isCell() || it.isConvertedCell()) }
      .map { it.toCellCertificateLocation(approvalRequest) }

    return CellCertificateLocation(
      locationType = locationType,
      locationCode = getLocationCode(),
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
      workingCapacity = if (draftApprovalLocationIsPartOfHierarchy(approvalRequest)) {
        getWorkingCapacityIgnoringInactiveStatus()
      } else {
        calcWorkingCapacity()
      },
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
      usedForTypes = getUsedForValuesAsCSV(),
      accommodationTypes = getAccommodationTypesAsCSV(),
      specialistCellTypes = getSpecialistCellTypesAsCSV(),
      convertedCellType = if (this is Cell && isConvertedCell()) {
        convertedCellType
      } else {
        null
      },
      subLocations = subLocations.toSortedSet(),
    )
  }

  private fun draftApprovalLocationIsPartOfHierarchy(approvalRequest: CertificationApprovalRequest): Boolean = if (approvalRequest.approvalType == ApprovalType.DRAFT) {
    val locationRequest = approvalRequest as? LocationCertificationApprovalRequest
    locationRequest?.location?.let { location ->
      isInHierarchy(location) || findLocation(location.getKey()) != null
    } ?: false
  } else {
    false
  }

  private fun toCertificationApprovalRequestLocation(includeDraft: Boolean = true): CertificationApprovalRequestLocation {
    val subLocations: List<CertificationApprovalRequestLocation> = getResidentialLocationsBelowThisLevel()
      .filter { (it.isStructural() || it.isCell() || it.isConvertedCell()) }
      .map { it.toCertificationApprovalRequestLocation(includeDraft) }

    return CertificationApprovalRequestLocation(
      locationType = locationType,
      locationCode = getLocationCode(),
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
      maxCapacity = calcMaxCapacity(includeDraft),
      workingCapacity = calcWorkingCapacity(includeDraft),
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(includeDraft),
      usedForTypes = getUsedForValuesAsCSV(),
      accommodationTypes = getAccommodationTypesAsCSV(),
      specialistCellTypes = getSpecialistCellTypesAsCSV(),
      convertedCellType = if (this is Cell && isConvertedCell()) {
        convertedCellType
      } else {
        null
      },
      subLocations = subLocations.toSortedSet(),
    )
  }

  private fun getSpecialistCellTypesAsCSV(): String? = getSpecialistCellTypes().map { it.specialistCellType }.distinct().takeIf { it.isNotEmpty() }
    ?.joinToString(separator = ",") { it.name }

  private fun getAccommodationTypesAsCSV(): String? = getAccommodationTypes().takeIf { it.isNotEmpty() }?.joinToString(separator = ",") { it.name }

  private fun getUsedForValuesAsCSV(): String? = getUsedForValues().map { it.usedFor }.distinct().takeIf { it.isNotEmpty() }
    ?.joinToString(separator = ",") { it.name }

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
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
    ),
    topLevelApprovalLocationId = findHighestLevelPending(includeDrafts = true)?.id,
    pendingApprovalRequestId = getPendingApprovalRequest()?.id,

    pendingChanges = if (hasPendingCertificationApproval() || hasPendingChangesBelowThisLevel() || isDraft()) {
      PendingChangeDto(
        maxCapacity = calcMaxCapacity(true),
        workingCapacity = calcWorkingCapacity(true),
        certifiedNormalAccommodation = calcCertifiedNormalAccommodation(true),
      )
    } else {
      null
    },

    certifiedCell = hasCertifiedCells(),
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
      certifiedNormalAccommodation = calcCertifiedNormalAccommodation(),
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
    certifiedNormalAccommodation: Int = 0,
    userOrSystemInContext: String,
    amendedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    if (isCell() || isVirtualResidentialLocation()) {
      if (maxCapacity != (capacity?.maxCapacity ?: 0) ||
        certifiedNormalAccommodation != (capacity?.certifiedNormalAccommodation ?: 0) ||
        workingCapacity != (capacity?.workingCapacity ?: 0)
      ) {
        log.info("${getKey()}: Updating max capacity from ${capacity?.maxCapacity ?: 0} to $maxCapacity, CNA from ${capacity?.certifiedNormalAccommodation ?: 0} to $certifiedNormalAccommodation and working capacity from ${capacity?.workingCapacity ?: 0} to $workingCapacity")

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
        addHistory(
          LocationAttribute.CERTIFIED_CAPACITY,
          capacity?.certifiedNormalAccommodation?.let { calcCertifiedNormalAccommodation().toString() } ?: "None",
          certifiedNormalAccommodation.toString(),
          userOrSystemInContext,
          amendedDate,
          linkedTransaction,
        )

        if (capacity != null) {
          capacity?.setCapacity(maxCapacity, workingCapacity, certifiedNormalAccommodation)
        } else {
          capacity = Capacity(
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            certifiedNormalAccommodation = certifiedNormalAccommodation,
          )
        }

        this.updatedBy = userOrSystemInContext
        this.whenUpdated = amendedDate
      } else {
        log.warn("Capacity cannot be set, not a cell or virtual location")
      }
    }
  }
}
fun validateCapacity(
  locationKey: String,
  certifiedNormalAccommodation: Int,
  workingCapacity: Int,
  maxCapacity: Int,
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
  specialistCellTypes: Set<SpecialistCellType> = emptySet(),
  permanentlyDeactivated: Boolean = false,
  temporarilyDeactivated: Boolean = false,
  virtualLocation: Boolean = false,
) {
  if (workingCapacity > 99) {
    throw CapacityException(
      locationKey,
      "Working capacity must be less than 100",
      ErrorCode.WorkingCapacityLimitExceeded,
    )
  }
  if (maxCapacity > 99) {
    throw CapacityException(locationKey, "Max capacity must be less than 100", ErrorCode.MaxCapacityLimitExceeded)
  }
  if (workingCapacity > maxCapacity) {
    throw CapacityException(
      locationKey,
      "Working capacity ($workingCapacity) cannot be more than max capacity ($maxCapacity)",
      ErrorCode.WorkingCapacityExceedsMaxCapacity,
    )
  }
  if (maxCapacity == 0 && !permanentlyDeactivated) {
    throw CapacityException(locationKey, "Max capacity cannot be zero", ErrorCode.MaxCapacityCannotBeZero)
  }

  if (!(permanentlyDeactivated || temporarilyDeactivated || virtualLocation)) {
    if (!isCapacityValid(
        workingCapacity,
        certifiedNormalAccommodation,
        accommodationType = accommodationType,
        specialistCellTypes = specialistCellTypes,
      )
    ) {
      throw CapacityException(
        locationKey,
        "Normal accommodation must not have a CNA or working capacity of 0",
        ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
      )
    }
  }
}

private fun isCapacityValid(
  workingCapacity: Int,
  certifiedNormalAccommodation: Int,
  accommodationType: AccommodationType,
  specialistCellTypes: Set<SpecialistCellType>? = null,
): Boolean {
  val cellIsSpecialistCellAllowingZeroCapacity =
    (specialistCellTypes?.isNotEmpty() ?: false && specialistCellTypes.all { it.affectsCapacity }) || accommodationType != AccommodationType.NORMAL_ACCOMMODATION
  return cellIsSpecialistCellAllowingZeroCapacity || (certifiedNormalAccommodation != 0 && workingCapacity != 0)
}

fun isCapacityRequired(
  typesToCheck: Set<SpecialistCellType>,
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
): Boolean = accommodationType == AccommodationType.NORMAL_ACCOMMODATION &&
  (typesToCheck.isEmpty() || typesToCheck.any { !it.affectsCapacity })

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
