package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PendingChangeDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CapacityChangeApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CellMarkChangeApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequestLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ConvertToCellApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ConvertToNonResidentialCellApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.DraftChangeApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.LocationCertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.SanitationChangeApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.SpecialistCellTypeChangeApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ChangesCannotBeMadeWithoutCertificationApprovalException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
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
  status: LocationStatus,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = null,
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: SortedSet<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
  capacity: Capacity? = null,

  var temporarilyOffCellCert: Boolean = false,

  var cellMark: String? = null,

  @Column(nullable = false)
  var certifiedCell: Boolean = false,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  var attributes: SortedSet<ResidentialAttribute> = sortedSetOf(),

  @Enumerated(EnumType.STRING)
  var accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  var usedFor: SortedSet<CellUsedFor> = sortedSetOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  var specialistCellTypes: SortedSet<SpecialistCell> = sortedSetOf(),

  @Enumerated(EnumType.STRING)
  var convertedCellType: ConvertedCellType? = null,

  private var otherConvertedCellType: String? = null,

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

  override fun markAsTemporarilyOffCellCert() {
    this.temporarilyOffCellCert = true
  }

  override fun removeTemporarilyOffCellCert() {
    this.temporarilyOffCellCert = false
  }

  override fun isShortTermInactive(): Boolean = this.temporarilyOffCellCert

  fun getOldWorkingCapacity(): Int? = findPendingLeafLocation(true)?.currentWorkingCapacity ?: capacity?.workingCapacity

  fun getWorkingCapacity(includePending: Boolean = false): Int? = findPendingLeafLocation(includePending)?.workingCapacity ?: if (isActiveAndAllParentsActive() || (includePending && isDraft())) {
    getCurrentlyHeldWorkingCapacity()
  } else {
    null
  }

  /**
   * Working capacity to record on a cell certificate. As [getWorkingCapacity] but a cell identified as
   * temporarily off the cell certificate (INACTIVE_TEMP) keeps its certified working capacity rather than
   * reporting 0, so the certificate reflects the true certified value while the cell is temporarily reduced.
   */
  fun getWorkingCapacityForCertificate(): Int? = findPendingLeafLocation(false)?.workingCapacity ?: if (isActiveAndAllParentsActive() || isShortTermInactive()) {
    getCurrentlyHeldWorkingCapacity()
  } else {
    null
  }

  fun getMaxCapacity(includePending: Boolean = false): Int? = findPendingLeafLocation(includePending)?.maxCapacity ?: capacity?.maxCapacity

  fun getCertifiedNormalAccommodation(includePending: Boolean = false): Int? = findPendingLeafLocation(includePending)?.certifiedNormalAccommodation ?: capacity?.certifiedNormalAccommodation

  private fun findPendingLeafLocation(includePending: Boolean): CertificationApprovalRequestLocation? {
    val topLevelLocation = getPendingCapacityChange(includePending)?.getTopLevelLocation() ?: return null
    return if (topLevelLocation.pathHierarchy == this.getPathHierarchy()) {
      topLevelLocation
    } else {
      topLevelLocation.findSubLocations().firstOrNull { it.pathHierarchy == this.getPathHierarchy() }
    }
  }

  fun getDoorCellMark(includePending: Boolean = false): String? {
    val pendingChange = if (includePending) {
      getPendingApprovalRequest() as? CellMarkChangeApprovalRequest
    } else {
      null
    }
    return pendingChange?.cellMark ?: cellMark
  }

  fun getSanitationOfCell(includePending: Boolean = false): Boolean? {
    val pendingChange = if (includePending) {
      getPendingApprovalRequest() as? SanitationChangeApprovalRequest
    } else {
      null
    }
    return pendingChange?.inCellSanitation ?: inCellSanitation
  }

  private fun getPendingCapacityChange(shouldIncludePending: Boolean) = if (shouldIncludePending) {
    getPendingApprovalRequest()
  } else {
    null
  }

  fun isCertified() = certifiedCell

  override fun getDerivedLocationType() = if (isConvertedCell()) {
    LocationType.ROOM
  } else {
    locationType
  }

  override fun isCell() = !isConvertedCell()

  override fun isConvertedCell() = convertedCellType != null

  fun getOtherConvertedCellType(): String? = otherConvertedCellType

  private fun getConvertedCellTypeSummary() = listOfNotBlank(convertedCellType?.description, otherConvertedCellType).joinToString(" - ")

  fun convertToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
    applyConversionToNonResidentialCell(convertedCellType, otherConvertedCellType, userOrSystemInContext, clock, linkedTransaction)
  }

  /**
   * Applies the conversion to a non-residential room without the pending-approval guard. Used both by the public
   * [convertToNonResidentialCell] and when applying a previously requested conversion as part of approving a
   * [ConvertToNonResidentialCellApprovalRequest] (where the request is still PENDING at the point of conversion).
   */
  internal fun applyConversionToNonResidentialCell(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.STATUS,
      this.getDerivedStatus().description,
      DerivedLocationStatus.NON_RESIDENTIAL.description,
      userOrSystemInContext,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    applyNonResidentialCellTypeUpdate(convertedCellType, otherConvertedCellType, userOrSystemInContext, clock, linkedTransaction)
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
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
    applyNonResidentialCellTypeUpdate(convertedCellType, otherConvertedCellType, userOrSystemInContext, clock, linkedTransaction)
  }

  internal fun applyNonResidentialCellTypeUpdate(convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
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
    temporarilyOffCellCert = false
  }

  fun convertToCell(accommodationType: AllowedAccommodationTypeForConversion, usedForTypes: List<UsedForType>? = null, specialistCellTypes: Set<SpecialistCellType>? = null, certifiedNormalAccommodation: Int? = null, maxCapacity: Int = 0, workingCapacity: Int = 0, cellMark: String? = null, inCellSanitation: Boolean? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
    applyConvertToCell(accommodationType.mapsTo, usedForTypes, specialistCellTypes, certifiedNormalAccommodation, maxCapacity, workingCapacity, cellMark, inCellSanitation, userOrSystemInContext, clock, linkedTransaction)
  }

  /**
   * Converts a non-residential room back to a cell without the pending-approval guard. Used both by the public
   * [convertToCell] and when applying a previously requested conversion as part of approving a
   * [ConvertToCellApprovalRequest] (where the request is still PENDING at the point of conversion).
   */
  internal fun applyConvertToCell(accommodationType: AccommodationType, usedForTypes: List<UsedForType>? = null, specialistCellTypes: Set<SpecialistCellType>? = null, certifiedNormalAccommodation: Int? = null, maxCapacity: Int = 0, workingCapacity: Int = 0, cellMark: String? = null, inCellSanitation: Boolean? = null, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
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

    setAccommodationTypeForCell(accommodationType, userOrSystemInContext, clock, linkedTransaction)

    usedForTypes?.forEach {
      addUsedFor(it, userOrSystemInContext, clock, linkedTransaction)
    }

    specialistCellTypes?.let { updateSpecialistCellTypes(specialistCellTypes = it, clock = clock, userOrSystemInContext = userOrSystemInContext, linkedTransaction = linkedTransaction) }

    setCapacity(maxCapacity = maxCapacity, workingCapacity = workingCapacity, certifiedNormalAccommodation = certifiedNormalAccommodation ?: workingCapacity, userOrSystemInContext = userOrSystemInContext, amendedDate = amendedDate, linkedTransaction = linkedTransaction)

    // Apply the door number / sanitation only when supplied, leaving the existing values untouched otherwise.
    cellMark?.let { setCellDoorMark(it, userOrSystemInContext, amendedDate, linkedTransaction) }
    inCellSanitation?.let { setSanitationOfCell(it, userOrSystemInContext, amendedDate, linkedTransaction) }

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

  fun setCellDoorMark(newCellMark: String, amendedBy: String, amendedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.CELL_MARK,
      cellMark,
      newCellMark,
      amendedBy,
      amendedDate,
      linkedTransaction,
    )
    cellMark = newCellMark
  }

  fun setSanitationOfCell(newInCellSanitation: Boolean, amendedBy: String, amendedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    addHistory(
      LocationAttribute.IN_CELL_SANITATION,
      this.inCellSanitation.toString(),
      newInCellSanitation.toString(),
      amendedBy,
      amendedDate,
      linkedTransaction,
    )
    this.inCellSanitation = newInCellSanitation
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

  override fun update(
    upsert: PatchLocationRequest,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
    approvalRequired: Boolean,
  ): Cell {
    super.update(upsert, userOrSystemInContext, clock, linkedTransaction, approvalRequired)

    if (upsert is PatchResidentialLocationRequest) {
      setAccommodationTypeForCell(upsert.accommodationType ?: this.accommodationType, userOrSystemInContext, clock, linkedTransaction)

      upsert.cellMark?.let { cellMark ->
        if (approvalRequired) {
          throw ChangesCannotBeMadeWithoutCertificationApprovalException(getKey())
        }
        setCellDoorMark(
          newCellMark = cellMark,
          amendedBy = userOrSystemInContext,
          amendedDate = LocalDateTime.now(clock),
          linkedTransaction = linkedTransaction,
        )
      }

      upsert.inCellSanitation?.let { inCellSanitation ->
        if (approvalRequired) {
          throw ChangesCannotBeMadeWithoutCertificationApprovalException(getKey())
        }
        setSanitationOfCell(
          newInCellSanitation = inCellSanitation,
          amendedBy = userOrSystemInContext,
          amendedDate = LocalDateTime.now(clock),
          linkedTransaction = linkedTransaction,
        )
      }
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
    upsert.certifiedCell?.let { certifiedCell = it }

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

  fun getSpecialistCellTypesForCell(): Set<SpecialistCellType> = specialistCellTypes.map { it.specialistCellType }.toSet()

  fun getSpecialistCellTypesForCell(includePending: Boolean): Set<SpecialistCellType> {
    val pendingChange = if (includePending) {
      getPendingApprovalRequest() as? SpecialistCellTypeChangeApprovalRequest
    } else {
      null
    }
    return pendingChange?.getSpecialistCellTypesFromPendingList()?.toSet() ?: getSpecialistCellTypesForCell()
  }

  override fun processApproval(
    pendingApprovalRequest: CertificationApprovalRequest,
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
    clock: Clock,
  ) {
    when (pendingApprovalRequest) {
      is DraftChangeApprovalRequest -> {
        certifyCell(pendingApprovalRequest.requestedBy, approvedDate, linkedTransaction)
      }
      is CellMarkChangeApprovalRequest -> {
        setCellDoorMark(pendingApprovalRequest.cellMark, pendingApprovalRequest.requestedBy, approvedDate, linkedTransaction)
      }
      is SanitationChangeApprovalRequest -> {
        setSanitationOfCell(pendingApprovalRequest.inCellSanitation, pendingApprovalRequest.requestedBy, approvedDate, linkedTransaction)
      }
    }
  }

  fun requestApprovalForCapacityChange(
    requestedDate: LocalDateTime,
    requestedBy: String,
    newWorkingCapacity: Int,
    newMaxCapacity: Int,
    newCna: Int,
  ): LocationCertificationApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }

    return addApprovalToLocation(
      CapacityChangeApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        workingCapacity = newWorkingCapacity,
        maxCapacity = newMaxCapacity,
        certifiedNormalAccommodation = newCna,
      ),
    ) as CapacityChangeApprovalRequest
  }

  fun requestApprovalForCellMarkChange(
    requestedDate: LocalDateTime,
    requestedBy: String,
    cellMarkChange: String,
    reasonForChange: String,
  ): CellMarkChangeApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }

    return addApprovalToLocation(
      CellMarkChangeApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        reasonForChange = reasonForChange,
        cellMark = cellMarkChange,
        currentCellMark = cellMark,
      ),
    ) as CellMarkChangeApprovalRequest
  }

  fun requestApprovalForCellSanitationChange(
    requestedDate: LocalDateTime,
    requestedBy: String,
    inCellSanitationChange: Boolean,
    reasonForChange: String,
  ): SanitationChangeApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }
    return addApprovalToLocation(
      SanitationChangeApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        reasonForChange = reasonForChange,
        inCellSanitation = inCellSanitationChange,
        currentInCellSanitation = inCellSanitation,
      ),
    ) as SanitationChangeApprovalRequest
  }

  fun requestApprovalForSpecialistCellTypeChange(
    requestedDate: LocalDateTime,
    requestedBy: String,
    newSpecialistCellTypes: Set<SpecialistCellType>,
    workingCapacity: Int,
    maxCapacity: Int,
    certifiedNormalAccommodation: Int,
    reasonForChange: String?,
  ): SpecialistCellTypeChangeApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }
    return addApprovalToLocation(
      SpecialistCellTypeChangeApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        reasonForChange = reasonForChange,
        specialistCellTypes = newSpecialistCellTypes.joinToString(",") { it.name },
        workingCapacity = workingCapacity,
        maxCapacity = maxCapacity,
        certifiedNormalAccommodation = certifiedNormalAccommodation,
      ),
    ) as SpecialistCellTypeChangeApprovalRequest
  }

  fun requestApprovalForConvertToCell(
    requestedDate: LocalDateTime,
    requestedBy: String,
    accommodationType: AccommodationType,
    specialistCellTypes: Set<SpecialistCellType>?,
    certifiedNormalAccommodation: Int,
    maxCapacity: Int,
    workingCapacity: Int,
    usedForTypes: List<UsedForType>?,
    cellMark: String? = null,
    inCellSanitation: Boolean? = null,
    reasonForChange: String?,
  ): ConvertToCellApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }

    // Capture the current (pre-conversion) values so the UI can play back "current -> new". The
    // converted cell type is always captured; the accommodation type / used-for are captured only
    // when the proposed values are not already present in the parent's current values.
    val parent = getParent() as? ResidentialLocation
    val parentAccommodationTypes = parent?.getAccommodationTypes() ?: emptySet()
    val parentUsedForTypes = parent?.getUsedForValues()?.map { it.usedFor }?.toSet() ?: emptySet()

    val currentAccommodationTypes = if (accommodationType !in parentAccommodationTypes) {
      parentAccommodationTypes.joinToString(",") { it.name }.takeIf { it.isNotEmpty() }
    } else {
      null
    }
    val currentUsedForTypes = if (!parentUsedForTypes.containsAll(usedForTypes?.toSet() ?: emptySet())) {
      parentUsedForTypes.joinToString(",") { it.name }.takeIf { it.isNotEmpty() }
    } else {
      null
    }

    // Capture the resulting (post-conversion) accommodation types / used-for at the top-level wing so the UI can warn
    // that the change affects the levels above. The converting room is not yet a residential cell, so the resulting
    // set is the wing's current set plus the proposed type; only surfaced when that proposed type is not already held
    // by the wing (i.e. the wing's set of accommodation types genuinely changes).
    val topLevel = findTopLevelResidentialLocation()
    val topLevelAccommodationTypes = topLevel.getAccommodationTypes(includeDraftCells = true)
    val affectsLevelsAbove = this != topLevel && accommodationType !in topLevelAccommodationTypes
    val resultingTopLevelAccommodationTypes = if (affectsLevelsAbove) {
      (topLevelAccommodationTypes + accommodationType).toCsvOrNull()
    } else {
      null
    }
    val resultingTopLevelUsedFor = if (affectsLevelsAbove) {
      (topLevel.getUsedForTypes(includeDraftCells = true) + (usedForTypes ?: emptyList())).toCsvOrNull()
    } else {
      null
    }

    return addApprovalToLocation(
      ConvertToCellApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        reasonForChange = reasonForChange,
        accommodationType = accommodationType,
        specialistCellTypes = specialistCellTypes?.joinToString(",") { it.name },
        usedForTypes = usedForTypes?.joinToString(",") { it.name },
        workingCapacity = workingCapacity,
        maxCapacity = maxCapacity,
        certifiedNormalAccommodation = certifiedNormalAccommodation,
        currentConvertedCellType = convertedCellType,
        currentOtherConvertedCellType = otherConvertedCellType,
        currentAccommodationTypes = currentAccommodationTypes,
        currentUsedForTypes = currentUsedForTypes,
        cellMark = cellMark,
        // Only capture the current value when a new one is proposed, so the UI has a "current -> new" to show.
        currentCellMark = cellMark?.let { this.cellMark },
        inCellSanitation = inCellSanitation,
        currentInCellSanitation = inCellSanitation?.let { this.inCellSanitation },
        topLevelAccommodationTypes = resultingTopLevelAccommodationTypes,
        topLevelUsedFor = resultingTopLevelUsedFor,
      ),
    ) as ConvertToCellApprovalRequest
  }

  fun requestApprovalForConvertToNonResidentialCell(
    requestedDate: LocalDateTime,
    requestedBy: String,
    convertedCellType: ConvertedCellType,
    otherConvertedCellType: String?,
    reasonForChange: String?,
  ): ConvertToNonResidentialCellApprovalRequest {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(getKey())
    }
    return addApprovalToLocation(
      ConvertToNonResidentialCellApprovalRequest(
        location = this,
        requestedBy = requestedBy,
        requestedDate = requestedDate,
        reasonForChange = reasonForChange,
        convertedCellType = convertedCellType,
        otherConvertedCellType = otherConvertedCellType,
        currentInCellSanitation = getSanitationOfCell(false),
      ),
    ) as ConvertToNonResidentialCellApprovalRequest
  }

  /**
   * Clears a temporary deactivation prior to applying a conversion to a non-residential room. Unlike
   * [Location.reactivate] this does NOT re-certify the cell or restore capacity, because the conversion that
   * follows de-certifies the cell and removes its capacity.
   */
  fun clearTemporaryDeactivationForConversion(userOrSystemInContext: String, linkedTransaction: LinkedTransaction) {
    if (!isTemporarilyDeactivated()) return
    addHistory(
      LocationAttribute.STATUS,
      getDerivedStatus().description,
      LocationStatus.ACTIVE.description,
      userOrSystemInContext,
      linkedTransaction.txStartTime,
      linkedTransaction,
    )
    status = LocationStatus.ACTIVE
    deactivatedReason = null
    deactivationReasonDescription = null
    deactivatedDate = null
    proposedReactivationDate = null
    planetFmReference = null
    deactivatedBy = null
    temporarilyOffCellCert = false
    updatedBy = userOrSystemInContext
    whenUpdated = linkedTransaction.txStartTime
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
    cellCertificateLocation: CellCertificateLocation?,
  ): LocationDto = super.toDto(
    includeChildren = includeChildren,
    includeParent = includeParent,
    includeHistory = includeHistory,
    countInactiveCells = countInactiveCells,
    includeNonResidential = includeNonResidential,
    useHistoryForUpdate = useHistoryForUpdate,
    countCells = countCells,
    formatLocalName = formatLocalName,
    cellCertificateLocation = cellCertificateLocation,
  ).copy(
    oldWorkingCapacity = if (isTemporarilyDeactivated()) {
      calcOldWorkingCapacity()
    } else {
      null
    },
    pendingChanges = if (hasPendingCertificationApproval() || isDraft()) {
      PendingChangeDto(
        maxCapacity = calcMaxCapacity(true),
        workingCapacity = calcWorkingCapacity(true),
        certifiedNormalAccommodation = calcCertifiedNormalAccommodation(true),
        cellMark = getDoorCellMark(true),
        inCellSanitation = getSanitationOfCell(true),
        specialistCellTypes = getSpecialistCellTypesForCell(true),
      )
    } else {
      null
    },
    convertedCellType = convertedCellType,
    otherConvertedCellType = otherConvertedCellType,
    inCellSanitation = getSanitationOfCell(false),
    cellMark = getDoorCellMark(false),
  )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation = super.toLegacyDto(includeHistory = includeHistory).copy(
    ignoreWorkingCapacity = false,
    capacity = capacity?.toDto(),
    certifiedCell = certifiedCell,
  )
}
