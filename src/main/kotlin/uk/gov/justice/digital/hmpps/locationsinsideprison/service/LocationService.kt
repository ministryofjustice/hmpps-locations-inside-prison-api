package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellAttributes
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInitialisationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingAndStructureRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationPrefixDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PrisonHierarchyDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.AlreadyDeactivatedLocationException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.BulkPermanentDeactivationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellWithSpecialistCellTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DuplicateLocalNameForSameHierarchyException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeDeletedWhenNotDraftException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationContainsPrisonersException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationPrefixNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LockedLocationCannotBeUpdatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReasonForDeactivationMustBeProvidedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateCapacityRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.function.Predicate
import kotlin.collections.filter
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class LocationService(
  private val locationRepository: LocationRepository,
  private val nonResidentialLocationRepository: NonResidentialLocationRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val signedOperationCapacityRepository: PrisonConfigurationRepository,
  private val cellLocationRepository: CellLocationRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val entityManager: EntityManager,
  private val prisonerLocationService: PrisonerLocationService,
  private val prisonService: PrisonService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService,
  private val activePrisonService: ActivePrisonService,
  @Qualifier("residentialGroups") private val groupsProperties: Properties,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getLocationById(
    id: UUID,
    includeChildren: Boolean = false,
    includeHistory: Boolean = false,
    formatLocalName: Boolean = false,
  ): LocationDTO? = locationRepository.findById(id).getOrNull()?.toDto(
    includeChildren = includeChildren,
    includeHistory = includeHistory,
    formatLocalName = formatLocalName,
  )

  fun getTransaction(txId: UUID) = linkedTransactionRepository.findById(txId).getOrNull()?.toDto()

  @Transactional
  fun deleteLocation(id: UUID): LocationDTO = locationRepository.findById(id).getOrNull()?.also { locationToDelete ->
    val locationsAffected = locationToDelete.findSubLocations(true).plus(locationToDelete)
    if (!locationsAffected.all { it.isDraft() }) {
      throw LocationCannotBeDeletedWhenNotDraftException("Cannot delete locations that are not in DRAFT state")
    }

    val locationsToDelete = locationsAffected.map { it.getKey() }
    val tx = createLinkedTransaction(
      prisonId = locationToDelete.prisonId,
      type = TransactionType.DELETE,
      detail = "Deleted locations: $locationsToDelete",
    )

    locationsAffected.forEach {
      locationRepository.deleteLocationById(it.id!!)
    }

    log.info("Deleted locations: $locationsToDelete")
    telemetryClient.trackEvent(
      "Deleted Locations",
      mapOf(
        "locations" to locationsToDelete.joinToString(","),
      ),
      null,
    )
    tx.txEndTime = LocalDateTime.now(clock)
  }?.toDto(includeChildren = true)
    ?: throw LocationNotFoundException(id.toString())

  fun getLocationByPrison(prisonId: String): List<LocationDTO> = locationRepository.findAllByPrisonIdOrderByPathHierarchy(prisonId)
    .filter { !it.isPermanentlyDeactivated() }
    .map {
      it.toDto()
    }
    .sortedBy { it.getKey() }

  fun getLocationGroupsForPrison(prisonId: String): List<LocationGroupDto> {
    val groups = locationGroupFromPropertiesService.getLocationGroups(prisonId)
    return groups.ifEmpty {
      residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
        .filter { it.isActiveAndAllParentsActive() && it.isStructural() }
        .map {
          it.toLocationGroupDto()
        }
        .sortedWith(NaturalOrderComparator())
    }
  }

  fun getPrisonResidentialHierarchy(prisonId: String, includeVirtualLocations: Boolean = false, maxLevel: Int? = null, includeInactive: Boolean = false): List<PrisonHierarchyDto> = residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
    .filter {
      if (includeInactive) {
        !it.isPermanentlyDeactivated()
      } else {
        it.isActiveAndAllParentsActive()
      } &&
        (it.isStructural() || (includeVirtualLocations && it.isVirtualResidentialLocation()))
    }
    .map {
      it.toPrisonHierarchyDto(maxLevel, includeInactive)
    }
    .sortedWith(NaturalOrderComparator())

  fun getLocationPrefixFromGroup(prisonId: String, group: String): LocationPrefixDto {
    val agencyGroupKey = "${prisonId}_$group"

    val pattern = groupsProperties.getProperty(agencyGroupKey)
      ?: throw LocationPrefixNotFoundException(agencyGroupKey)

    val locationPrefix = pattern
      .replace(".", "")
      .replace("+", "")

    return LocationPrefixDto(locationPrefix)
  }

  fun getCellLocationsForGroup(prisonId: String, groupName: String): List<LocationDTO> = cellsInGroup(prisonId, groupName, cellLocationRepository.findAllByPrisonIdAndStatus(prisonId, LocationStatus.ACTIVE))
    .toMutableList()
    .map { it.toDto() }
    .sortedWith(NaturalOrderComparator())

  private fun cellsInGroup(
    prisonId: String,
    groupName: String?,
    cellsToFilter: List<Cell>,
  ): List<Cell> = if (groupName != null) {
    cellsToFilter.filter(locationGroupFilter(prisonId, groupName)::test)
  } else {
    cellsToFilter
  }

  private fun locationGroupFilter(prisonId: String, groupName: String): Predicate<Location> = try {
    locationGroupFromPropertiesService.locationGroupFilter(prisonId, groupName)
  } catch (_: EntityNotFoundException) {
    fallBackLocationGroupFilter(groupName)
  }

  private fun fallBackLocationGroupFilter(groupName: String): Predicate<Location> {
    val prefixToMatch = "${groupName.replace('_', '-')}-"
    return Predicate { it.getPathHierarchy().startsWith(prefixToMatch) }
  }

  fun getActiveNonResidentialLocationsForPrison(
    prisonId: String,
    sortByLocalName: Boolean = true,
    formatLocalName: Boolean = true,
  ): List<LocationDTO> {
    val results = nonResidentialLocationRepository.findAllByPrisonId(prisonId)
      .filter { it.isActiveAndAllParentsActive() }
      .filter { it.getCode() != "RTU" }
      .map { it.toDto(formatLocalName = formatLocalName) }
    return if (sortByLocalName) {
      results.sortedBy { it.localName }
    } else {
      results.sortedBy { it.getKey() }
    }
  }

  fun getLocationsByPrisonAndNonResidentialUsageType(
    prisonId: String,
    usageType: NonResidentialUsageType? = null,
    sortByLocalName: Boolean = false,
    formatLocalName: Boolean = false,
    filterParents: Boolean = true,
  ): List<LocationDTO> {
    val filteredByUsage = usageType?.let {
      nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(prisonId, usageType)
    } ?: nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(prisonId)

    val filteredResults = filteredByUsage
      .filter { it.getCode() != "RTU" }
      .filter { !filterParents || it.findSubLocations().intersect(filteredByUsage.toSet()).isEmpty() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      filteredResults.sortedBy { it.localName }
    } else {
      filteredResults
    }
  }

  fun getLocationByKey(key: String, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? = locationRepository.findOneByKey(key)?.toDto(
    includeChildren = includeChildren,
    includeHistory = includeHistory,
  )

  fun getLocationsByKeys(keys: List<String>): List<LocationDTO> = locationRepository.findAllByKeys(keys)
    .map { it.toDto() }
    .sortedBy { it.getKey() }

  fun getLocations(pageable: Pageable = PageRequest.of(0, 20, Sort.by("id"))): Page<LegacyLocation> = locationRepository.findAll(pageable).map(Location::toLegacyDto)

  fun getLocationByPrisonAndLocationType(
    prisonId: String,
    locationType: LocationType,
    sortByLocalName: Boolean = false,
    formatLocalName: Boolean = false,
  ): List<LocationDTO> {
    val rawResult = locationRepository.findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId, locationType)
      .filter { it.isActive() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      rawResult.sortedBy { it.localName }
    } else {
      rawResult.sortedBy { it.getKey() }
    }
  }

  @Transactional
  fun createResidentialLocation(request: CreateResidentialLocationRequest): LocationDTO {
    val parentLocation = request.parentId?.let {
      locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString())
    } ?: request.parentLocationKey?.let {
      locationRepository.findOneByKey(it) ?: throw LocationNotFoundException(it)
    }

    checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with the existing location

    val linkedTransaction = createLinkedTransaction(
      prisonId = request.prisonId,
      TransactionType.LOCATION_CREATE,
      "Create residential location ${request.code} in prison ${request.prisonId} under ${parentLocation?.getKey() ?: "top level"}",
    )

    val certificationApprovalRequired = activePrisonService.isCertificationApprovalRequired(request.prisonId)

    val locationToCreate = request.toNewEntity(getUsername(), clock, linkedTransaction = linkedTransaction, createInDraft = certificationApprovalRequired, parentLocation = parentLocation)

    val capacityChanged = request.isCell() && request.capacity != null

    val createdLocation = locationRepository.save(locationToCreate)

    log.info("Created Residential Location [${createdLocation.getKey()}]")
    trackLocationUpdate(createdLocation, "Created Residential Location")

    return createdLocation.toDto(includeParent = capacityChanged && !certificationApprovalRequired).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun createLinkedTransaction(prisonId: String, type: TransactionType, detail: String): LinkedTransaction {
    val linkedTransaction = LinkedTransaction(
      prisonId = prisonId,
      transactionType = type,
      transactionDetail = detail,
      transactionInvokedBy = getUsername(),
      txStartTime = LocalDateTime.now(clock),
    )
    return linkedTransactionRepository.save(linkedTransaction).also {
      log.info("Created linked transaction: $it")
    }
  }

  private fun getUsername() = authenticationHolder.username ?: SYSTEM_USERNAME

  @Transactional
  fun createNonResidentialLocation(request: CreateNonResidentialLocationRequest): LocationDTO {
    val parentLocation = getParentLocation(request.parentId)

    checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with the existing location

    val linkedTransaction = createLinkedTransaction(
      prisonId = request.prisonId,
      TransactionType.LOCATION_CREATE,
      "Create non-residential location ${request.code} in prison ${request.prisonId} under ${parentLocation?.getKey() ?: "top level"}",
    )

    val locationToCreate = request.toNewEntity(getUsername(), clock, linkedTransaction, parentLocation)

    val usageChanged = request.usage != null

    val createdLocation = locationRepository.save(locationToCreate)

    log.info("Created Non-Residential Location [${createdLocation.getKey()}]")
    trackLocationUpdate(createdLocation, "Created Non-Residential Location")

    return createdLocation.toDto(includeParent = usageChanged).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun createWing(request: CreateWingAndStructureRequest): LocationDTO {
    locationRepository.findOneByPrisonIdAndPathHierarchy(request.prisonId, request.wingCode)
      ?.let { throw LocationAlreadyExistsException("${request.prisonId}-${request.wingCode}") }

    val linkedTransaction = createLinkedTransaction(
      prisonId = request.prisonId,
      TransactionType.LOCATION_CREATE,
      "Create wing ${request.wingCode} in prison ${request.prisonId}",
    )

    val wing = request.toEntity(
      createdBy = getUsername(),
      clock = clock,
      linkedTransaction = linkedTransaction,
    )
    return locationRepository.save(wing).toDto(includeChildren = true, includeNonResidential = false).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun createCells(createCellsRequest: CellInitialisationRequest): LocationDTO {
    val parentLocation = createCellsRequest.parentLocation?.let {
      residentialLocationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString())
    }

    createCellsRequest.newLevelAboveCells?.let { newLevel ->
      with(newLevel) {
        // check that code doesn't clash with the existing location
        checkParentValid(
          parentLocation = parentLocation,
          code = levelCode,
          prisonId = createCellsRequest.prisonId,
        )
        // check that local-name is unique in this hierarchy
        levelLocalName?.let { localName ->
          if (findAllByPrisonIdTopParentAndLocalName(
              prisonId = createCellsRequest.prisonId,
              localName = localName,
              parentLocationId = parentLocation?.id,
            ).any()
          ) {
            throw DuplicateLocalNameForSameHierarchyException(
              localName = localName,
              topLocationKey = parentLocation?.getKey() ?: createCellsRequest.prisonId,
            )
          }
        }
      }
    }

    if (createCellsRequest.newLevelAboveCells == null && parentLocation == null) {
      throw ValidationException("Either a parent location or new level above cells must be provided")
    }

    val linkedTransaction = createCellsRequest.newLevelAboveCells?.let {
      createLinkedTransaction(
        prisonId = createCellsRequest.prisonId,
        TransactionType.LOCATION_CREATE,
        "Creating locations ${it.locationType} ${it.levelCode} in prison ${createCellsRequest.prisonId}",
      )
    } ?: createCellsRequest.cells?.let {
      createLinkedTransaction(
        prisonId = createCellsRequest.prisonId,
        TransactionType.LOCATION_CREATE,
        "Creating cells ${createCellsRequest.cells.joinToString { it.code }} in prison ${createCellsRequest.prisonId}",
      )
    } ?: throw ValidationException("Either a new level above cells must be provided or the cells")

    val newLocation = createCellsRequest.newLevelAboveCells?.let {
      residentialLocationRepository.save(
        it.createLocation(
          prisonId = createCellsRequest.prisonId,
          createdBy = getUsername(),
          clock = clock,
          linkedTransaction = linkedTransaction,
          parentLocation = parentLocation,
        ),
      )
    }

    val parentAboveCells = newLocation ?: parentLocation!!

    createCellsRequest.cells?.let { cells ->
      cells.forEach { cell ->
        // check that code doesn't clash with the existing location
        checkParentValid(
          parentLocation = parentAboveCells,
          code = cell.code,
          prisonId = createCellsRequest.prisonId,
        )

        val specialistCellTypesAffectingCapacity = cell.specialistCellTypes?.filter { it.affectsCapacity }
        if (!specialistCellTypesAffectingCapacity.isNullOrEmpty() && !(cell.certifiedNormalAccommodation == 0 && cell.workingCapacity == 0)) {
          throw CapacityException(
            cell.code,
            "Cannot have a 0 working capacity with normal accommodation and not specialist cell",
            ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
          )
        }
      }

      val createdCells = createCellsRequest.createCells(
        createdBy = getUsername(),
        clock = clock,
        linkedTransaction = linkedTransaction,
        location = parentAboveCells,
      )
      log.info("Created ${createdCells?.size} cells under location ${parentAboveCells.getKey()}")
    }

    return residentialLocationRepository.save(parentAboveCells).toDto(
      includeChildren = true,
      includeNonResidential = false,
    ).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateResidentialLocation(id: UUID, patchLocationRequest: PatchResidentialLocationRequest): UpdateLocationResult {
    val residentialLocation =
      residentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = createLinkedTransaction(
      prisonId = residentialLocation.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Update residential location ${residentialLocation.getKey()}",
    )

    return patchLocation(residentialLocation, patchLocationRequest, linkedTransaction).also {
      trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateResidentialLocation(
    key: String,
    patchLocationRequest: PatchResidentialLocationRequest,
  ): UpdateLocationResult {
    val residentialLocation = residentialLocationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)

    val linkedTransaction = createLinkedTransaction(
      prisonId = residentialLocation.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Update residential location ${residentialLocation.getKey()}",
    )

    return patchLocation(residentialLocation, patchLocationRequest, linkedTransaction).also {
      trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    id: UUID,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    return patchLocation(nonResLocation, patchLocationRequest, linkedTransaction).also {
      trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    key: String,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation = nonResidentialLocationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)
    val linkedTransaction = createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    return patchLocation(nonResLocation, patchLocationRequest, linkedTransaction).also {
      trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun patchLocation(
    location: Location,
    patchLocationRequest: PatchLocationRequest,
    linkedTransaction: LinkedTransaction,
  ): UpdateLocationResult {
    if (location.isLocationLocked()) {
      throw LockedLocationCannotBeUpdatedException(location.getKey())
    }

    val (codeChanged, oldParent, parentChanged) = updateCoreLocationDetails(location, patchLocationRequest, linkedTransaction)

    location.update(patchLocationRequest, getUsername(), clock, linkedTransaction)

    return UpdateLocationResult(
      location.toDto(
        includeChildren = codeChanged || parentChanged,
        includeParent = parentChanged,
        includeNonResidential = false,
      ),
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  @Transactional
  fun updateResidentialLocationUsedForTypes(id: UUID, usedFor: Set<UsedForType>): LocationDTO {
    val residentialLocation = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = createLinkedTransaction(
      prisonId = residentialLocation.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Updated Used for types for below location ${residentialLocation.getKey()}",
    )

    residentialLocation.updateCellUsedFor(
      usedFor,
      getUsername(),
      clock,
      linkedTransaction,
    )

    log.info("Updated Used for types for below Location [$residentialLocation.getKey()]")
    trackLocationUpdate(residentialLocation, "Updated Used For Type below Residential Location")

    return residentialLocation.toDto(includeChildren = true, includeNonResidential = false).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun updateCoreLocationDetails(
    locationToUpdate: Location,
    patchLocationRequest: PatchLocationRequest,
    linkedTransaction: LinkedTransaction,
  ): UpdatedSummary {
    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(locationToUpdate.getKey())
    }

    val codeChanged = patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getCode()
    val oldParent = locationToUpdate.getParent()
    val parentChanged = when {
      patchLocationRequest.removeParent == true ->
        patchLocationRequest.parentId == null && patchLocationRequest.parentLocationKey == null
      patchLocationRequest.parentId != null ->
        patchLocationRequest.parentId != oldParent?.id
      patchLocationRequest.parentLocationKey != null ->
        patchLocationRequest.parentLocationKey != oldParent?.getKey()
      else -> false
    }

    if (codeChanged || parentChanged) {
      val newCode = patchLocationRequest.code ?: locationToUpdate.getCode()

      val theParent = when {
        patchLocationRequest.removeParent == true -> null
        patchLocationRequest.parentId != null -> locationRepository.findById(patchLocationRequest.parentId!!)
          .getOrNull() ?: throw LocationNotFoundException(patchLocationRequest.parentId.toString())
        patchLocationRequest.parentLocationKey != null -> locationRepository.findOneByKey(patchLocationRequest.parentLocationKey!!)
          ?: throw LocationNotFoundException(patchLocationRequest.parentLocationKey!!)
        else -> oldParent
      }

      checkParentValid(theParent, newCode, locationToUpdate.prisonId)

      if (parentChanged && theParent?.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      locationToUpdate.setParent(theParent)

      if (parentChanged) {
        locationToUpdate.addHistory(
          LocationAttribute.PARENT_LOCATION,
          oldParent?.id?.toString(),
          theParent?.id?.toString(),
          getUsername(),
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
    return UpdatedSummary(codeChanged = codeChanged, oldParent = oldParent, parentChanged = parentChanged)
  }

  @Transactional
  fun updateCellCapacity(id: UUID, maxCapacity: Int, workingCapacity: Int, linkedTransaction: LinkedTransaction? = null): LocationDTO {
    val locCapChange = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locCapChange.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(locCapChange.getKey())
    }

    val prisoners = prisonerLocationService.prisonersInLocations(locCapChange)
    if (maxCapacity < prisoners.size) {
      throw CapacityException(
        locCapChange.getKey(),
        "Max capacity ($maxCapacity) cannot be decreased below current cell occupancy (${prisoners.size})",
        ErrorCode.MaxCapacityCannotBeBelowOccupancyLevel,
      )
    }

    val pendingChange = activePrisonService.isCertificationApprovalRequired(locCapChange.prisonId) && locCapChange.calcMaxCapacity(true) != maxCapacity

    val trackingTx = linkedTransaction ?: createLinkedTransaction(
      prisonId = locCapChange.prisonId,
      type = if (pendingChange) TransactionType.PENDING_CELL_CHANGE else TransactionType.CAPACITY_CHANGE,
      detail = if (pendingChange) {
        "Pending max capacity change for ${locCapChange.getKey()} from ${locCapChange.capacity?.maxCapacity} to $maxCapacity"
      } else {
        "Capacities for ${locCapChange.getKey()} w/c changed from ${locCapChange.calcWorkingCapacity()} to $workingCapacity and max capacity changed from ${locCapChange.capacity?.maxCapacity} to $maxCapacity"
      },
    )

    locCapChange.setCapacity(
      maxCapacity = maxCapacity,
      workingCapacity = workingCapacity,
      userOrSystemInContext = getUsername(),
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = trackingTx,
    )

    if (pendingChange) {
      telemetryClient.trackEvent(
        "Pending capacity change",
        mapOf(
          "id" to id.toString(),
          "key" to locCapChange.getKey(),
          "maxCapacity" to maxCapacity.toString(),
        ),
        null,
      )
    } else {
      telemetryClient.trackEvent(
        "Capacity updated",
        mapOf(
          "id" to id.toString(),
          "key" to locCapChange.getKey(),
          "maxCapacity" to maxCapacity.toString(),
          "workingCapacity" to workingCapacity.toString(),
        ),
        null,
      )
    }

    return locCapChange.toDto(includeParent = !pendingChange, includeNonResidential = false).also {
      trackingTx.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateSpecialistCellTypes(id: UUID, specialistCellTypes: Set<SpecialistCellType>): LocationDTO {
    val cell = cellLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (cell.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(cell.getKey())
    }

    // Check that the workingCapacity is not set to 0 for normal accommodations when removing the specialist cell types
    if (specialistCellTypes.isEmpty() && cell.accommodationType == AccommodationType.NORMAL_ACCOMMODATION && cell.getWorkingCapacity() == 0) {
      throw CapacityException(
        cell.getKey(),
        "Cannot removes specialist cell types for a normal accommodation with a working capacity of 0",
        ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
      )
    }

    val linkedTransaction = createLinkedTransaction(
      prisonId = cell.prisonId,
      TransactionType.CELL_TYPE_CHANGES,
      "Update cell types for ${cell.getKey()}",
    )

    cell.updateSpecialistCellTypes(
      specialistCellTypes,
      getUsername(),
      clock,
      linkedTransaction,
    )
    log.info("Updated specialist cell types = $specialistCellTypes")

    telemetryClient.trackEvent(
      "Specialist cell types updated",
      mapOf(
        "id" to id.toString(),
        "key" to cell.getKey(),
        "specialistCellTypes" to specialistCellTypes.toString(),
      ),
      null,
    )
    return cell.toDto(includeParent = false, includeNonResidential = false).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateLocalName(id: UUID, updateLocationLocalNameRequest: UpdateLocationLocalNameRequest): LocationDTO {
    val location = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (location.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(location.getKey())
    }

    val linkedTransaction = createLinkedTransaction(
      prisonId = location.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Updated local name for ${location.getKey()}",
    )

    with(updateLocationLocalNameRequest) {
      if (localName != null) {
        val topLevelLocation = location.findTopLevelLocation()
        if (findAllByPrisonIdTopParentAndLocalName(
            prisonId = location.prisonId,
            localName = localName,
            parentLocationId = location.getParent()?.id,
          ).any { it.id != id }
        ) {
          throw DuplicateLocalNameForSameHierarchyException(localName = localName, topLocationKey = topLevelLocation.getKey())
        }
      }

      location.updateLocalName(
        localName = localName,
        userOrSystemInContext = updatedBy ?: getUsername(),
        clock = clock,
        linkedTransaction,
      )
    }

    log.info("Location local name updated [${location.getKey()}")
    trackLocationUpdate(location, "Location local name updated")

    return location.toDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun deactivateLocations(locationsToDeactivate: DeactivateLocationsRequest): Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val deactivatedLocations = mutableSetOf<Location>()

    val prisonId = locationsToDeactivate.locations.entries.firstOrNull()?.key?.let { id ->
      locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }.prisonId
    } ?: throw LocationNotFoundException("No location found in request")

    val linkedTransaction = createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.DEACTIVATION,
      "Deactivating locations",
    )

    locationsToDeactivate.locations.forEach { (id, deactivationDetail) ->
      val locationToDeactivate = locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (locationToDeactivate.isTemporarilyDeactivated()) {
        throw AlreadyDeactivatedLocationException(locationToDeactivate.getKey())
      }

      checkForPrisonersInLocation(locationToDeactivate)

      with(deactivationDetail) {
        if (deactivationReason == DeactivatedReason.OTHER && deactivationReasonDescription.isNullOrBlank()) {
          throw ReasonForDeactivationMustBeProvidedException(locationToDeactivate.getKey())
        }

        if (locationToDeactivate.temporarilyDeactivate(
            deactivatedReason = deactivationReason,
            deactivatedDate = LocalDateTime.now(clock),
            deactivationReasonDescription = deactivationReasonDescription,
            planetFmReference = planetFmReference,
            proposedReactivationDate = proposedReactivationDate,
            userOrSystemInContext = getUsername(),
            deactivatedLocations = deactivatedLocations,
            linkedTransaction = linkedTransaction,
          )
        ) {
          deactivatedLocations.forEach {
            trackLocationUpdate(it, "Temporarily Deactivated Location")
          }
        }
      }
    }

    val deactivatedLocationsDto = deactivatedLocations.map { it.toDto() }.toSet()
    return mapOf(
      InternalLocationDomainEventType.LOCATION_AMENDED to deactivatedLocations.flatMap { deactivatedLoc ->
        deactivatedLoc.getParentLocations().map { it.toDto() }
      }.toSet().minus(
        deactivatedLocationsDto,
      ).toList(),
      InternalLocationDomainEventType.LOCATION_DEACTIVATED to deactivatedLocationsDto.toList(),
    ).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun permanentlyDeactivateLocations(permanentDeactivationRequest: BulkPermanentDeactivationRequest, activeLocationCanBePermDeactivated: Boolean = false): List<LocationDTO> {
    val deactivatedLocations = mutableSetOf<Location>()

    val prisonId = permanentDeactivationRequest.locations.firstOrNull()?.let { key ->
      locationRepository.findOneByKey(key)?.prisonId ?: throw LocationNotFoundException(key)
    } ?: throw LocationNotFoundException("No location found in request")

    val linkedTransaction = createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.PERMANENT_DEACTIVATION,
      "Permanently deactivating locations",
    )

    permanentDeactivationRequest.locations.forEach { key ->
      val locationToPermanentlyDeactivate = locationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)

      if (locationToPermanentlyDeactivate.permanentlyDeactivate(
          reason = permanentDeactivationRequest.reason,
          deactivatedDate = LocalDateTime.now(clock),
          userOrSystemInContext = getUsername(),
          clock = clock,
          activeLocationCanBePermDeactivated = activeLocationCanBePermDeactivated,
          linkedTransaction = linkedTransaction,
        )
      ) {
        deactivatedLocations.add(locationToPermanentlyDeactivate)
      }
    }
    deactivatedLocations.forEach {
      trackLocationUpdate(it, "Permanently deactivated location")
    }
    return deactivatedLocations.map { it.toDto() }.also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateDeactivatedDetails(
    id: UUID,
    deactivatedReason: DeactivatedReason,
    deactivationReasonDescription: String? = null,
    proposedReactivationDate: LocalDate? = null,
    planetFmReference: String? = null,
  ): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (deactivatedReason == DeactivatedReason.OTHER && deactivationReasonDescription.isNullOrBlank()) {
      throw ReasonForDeactivationMustBeProvidedException(locationToUpdate.getKey())
    }

    val linkedTransaction = createLinkedTransaction(
      prisonId = locationToUpdate.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Temporarily deactivated location details updated for ${locationToUpdate.getKey()}",
    )

    if (locationToUpdate.isTemporarilyDeactivated()) {
      locationToUpdate.updateDeactivatedDetails(
        deactivatedReason = deactivatedReason,
        deactivationReasonDescription = deactivationReasonDescription,
        planetFmReference = planetFmReference,
        proposedReactivationDate = proposedReactivationDate,
        userOrSystemInContext = getUsername(),
        clock = clock,
        linkedTransaction = linkedTransaction,
      )

      telemetryClient.trackEvent(
        "Temporarily Deactivated Location Details Updated",
        mapOf(
          "id" to id.toString(),
          "prisonId" to locationToUpdate.prisonId,
          "path" to locationToUpdate.getPathHierarchy(),
        ),
        null,
      )
    }
    return locationToUpdate.toDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun permanentlyDeactivateLocation(
    id: UUID,
    reasonForPermanentDeactivation: String,
  ): LocationDTO {
    val locationToArchive = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val wasActiveBefore = locationToArchive.isActiveAndAllParentsActive()
    if (wasActiveBefore) {
      checkForPrisonersInLocation(locationToArchive)
    }

    permanentlyDeactivateLocations(
      BulkPermanentDeactivationRequest(
        reason = reasonForPermanentDeactivation,
        locations = listOf(locationToArchive.getKey()),
      ),
      activeLocationCanBePermDeactivated = wasActiveBefore,
    )
    return locationToArchive.toDto(includeChildren = wasActiveBefore, includeParent = wasActiveBefore)
  }

  @Transactional
  fun updateCapacityOfCellLocations(capacitiesToUpdate: UpdateCapacityRequest): CapacityUpdateResult {
    val updatedCapacities = mutableSetOf<ResidentialLocation>()
    val audit = mutableMapOf<String, List<CapacityChanges>>()

    val prisonId = capacitiesToUpdate.locations.entries.firstOrNull()?.key?.let { key ->
      locationRepository.findOneByKey(key)?.prisonId ?: throw LocationNotFoundException(key)
    } ?: throw LocationNotFoundException("No location found in request")

    val linkedTransaction = createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.CAPACITY_CHANGE,
      "Updating cell capacities",
    )

    capacitiesToUpdate.locations.forEach { (key, capacityChange) ->
      val changes = mutableListOf<CapacityChanges>()
      val location = residentialLocationRepository.findOneByKey(key)
      if (location != null) {
        if (!location.isPermanentlyDeactivated()) {
          if (location is Cell) {
            with(capacityChange) {
              if (location.getMaxCapacity(includePendingChange = true) != maxCapacity || location.getWorkingCapacity() != workingCapacity) {
                try {
                  val oldWorkingCapacity = location.getWorkingCapacity()
                  val oldMaxCapacity = location.getMaxCapacity(includePendingChange = true)
                  updateCellCapacity(
                    location.id!!,
                    maxCapacity = maxCapacity,
                    workingCapacity = workingCapacity,
                    linkedTransaction = linkedTransaction,
                  )
                  if (oldMaxCapacity != maxCapacity) {
                    changes.add(CapacityChanges(key, message = "Max capacity from $oldMaxCapacity ==> $maxCapacity", type = "maxCapacity", previousValue = oldMaxCapacity, newValue = maxCapacity))
                  }
                  if (oldWorkingCapacity != workingCapacity) {
                    changes.add(CapacityChanges(key, message = "Working capacity from $oldWorkingCapacity ==> $workingCapacity", type = "workingCapacity", previousValue = oldWorkingCapacity, newValue = workingCapacity))
                  }
                  updatedCapacities.add(location)
                } catch (e: Exception) {
                  changes.add(CapacityChanges(key, message = "Update failed: ${e.message}"))
                }
              } else {
                changes.add(CapacityChanges(key, message = "Capacity not changed"))
              }
              if (certifiedNormalAccommodation != null && certifiedNormalAccommodation != location.getCertifiedNormalAccommodation()) {
                val oldCertifiedNormalAccommodation = location.getCertifiedNormalAccommodation()
                location.setCertifiedNormalAccommodation(
                  certifiedNormalAccommodation,
                  getUsername(),
                  LocalDateTime.now(clock),
                  linkedTransaction,
                )
                changes.add(CapacityChanges(key, message = "Baseline CNA from $oldCertifiedNormalAccommodation ==> $certifiedNormalAccommodation", type = "CNA", previousValue = oldCertifiedNormalAccommodation, newValue = certifiedNormalAccommodation))
                updatedCapacities.add(location)
              }

              location.cellMark = cellMark
              location.inCellSanitation = inCellSanitation
            }
          } else {
            changes.add(CapacityChanges(key, message = "Not a cell"))
          }
        } else {
          changes.add(CapacityChanges(key, message = "Archived location"))
        }
      } else {
        changes.add(CapacityChanges(key, message = "Location not found"))
      }
      audit[key] = changes
      log.info(
        "$key: ${changes.joinToString {
          if (it.type == null) {
            "$it.message"
          } else {
            "${it.type}: ${it.previousValue} ==> ${it.newValue}"
          }
        }}",
      )
    }
    val updatedLocationsDto = updatedCapacities.map { it.toDto() }.toSet()

    audit.flatMap { it.value }.forEach { l ->
      var trackMap = mapOf("key" to l.key, "message" to l.message)
      l.type?.let { trackMap = trackMap.plus(it to "${l.previousValue} ==> ${l.newValue}") }
      telemetryClient.trackEvent("CAPACITY_CHANGE", trackMap, null)
    }
    return CapacityUpdateResult(
      updatedLocations = mapOf(
        InternalLocationDomainEventType.LOCATION_AMENDED to updatedCapacities.flatMap { changed ->
          changed.getParentLocations().map { it.toDto() }
        }.toSet().plus(updatedLocationsDto).toList(),
      ),
      audit = audit,
    ).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun reactivateLocations(locationsToReactivate: ReactivateLocationsRequest): Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val locationsReactivated = mutableSetOf<Location>()
    val amendedLocations = mutableSetOf<Location>()

    val prisonId = locationsToReactivate.locations.entries.firstOrNull()?.key?.let { id ->
      locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }.prisonId
    } ?: throw LocationNotFoundException("No location found in request")

    val linkedTransaction = createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.REACTIVATION,
      "Reactivating locations",
    )

    locationsToReactivate.locations.forEach { (id, reactivationDetail) ->
      val locationToUpdate = locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (locationToUpdate.isPermanentlyDeactivated()) {
        throw LocationCannotBeReactivatedException("Location [${locationToUpdate.getKey()}] permanently deactivated")
      }

      locationToUpdate.reactivate(
        userOrSystemInContext = getUsername(),
        clock = clock,
        reactivatedLocations = locationsReactivated,
        amendedLocations = amendedLocations,
        maxCapacity = reactivationDetail.capacity?.maxCapacity,
        workingCapacity = reactivationDetail.capacity?.workingCapacity,
        linkedTransaction = linkedTransaction,
      )

      if (reactivationDetail.cascadeReactivation) {
        locationToUpdate.findSubLocations().forEach { location ->
          location.reactivate(
            userOrSystemInContext = getUsername(),
            clock = clock,
            reactivatedLocations = locationsReactivated,
            amendedLocations = amendedLocations,
            linkedTransaction = linkedTransaction,
          )
        }
      }
    }

    locationsReactivated.forEach { trackLocationUpdate(it, "Re-activated Location") }
    return mapOf(
      InternalLocationDomainEventType.LOCATION_AMENDED to amendedLocations.map { it.toDto() }.toList(),
      InternalLocationDomainEventType.LOCATION_REACTIVATED to locationsReactivated.map { it.toDto() }.toList(),
    ).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun trackLocationUpdate(location: Location, trackDescription: String) {
    telemetryClient.trackEvent(
      trackDescription,
      mapOf(
        "id" to location.id!!.toString(),
        "prisonId" to location.prisonId,
        "path" to location.getPathHierarchy(),
      ),
      null,
    )
  }

  private fun trackLocationUpdate(location: LocationDTO) {
    telemetryClient.trackEvent(
      "Updated Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )
  }

  @Transactional
  fun updateNonResidentialCellType(
    id: UUID,
    convertedCellType: ConvertedCellType,
    otherConvertedCellType: String? = null,
  ): LocationDTO {
    val nonResCellToUpdate = cellLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (!nonResCellToUpdate.isConvertedCell()) {
      throw LocationNotFoundException("${nonResCellToUpdate.getKey()} is not a non-residential cell")
    }

    val linkedTransaction = createLinkedTransaction(
      prisonId = nonResCellToUpdate.prisonId,
      TransactionType.LOCATION_UPDATE,
      "Update cell type for ${nonResCellToUpdate.getKey()}",
    )

    nonResCellToUpdate.updateNonResidentialCellType(
      convertedCellType = convertedCellType,
      otherConvertedCellType = otherConvertedCellType,
      userOrSystemInContext = getUsername(),
      clock = clock,
      linkedTransaction = linkedTransaction,
    )

    trackLocationUpdate(nonResCellToUpdate, "Updated non-residential cell type")
    return nonResCellToUpdate.toDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun convertToNonResidentialCell(
    id: UUID,
    convertedCellType: ConvertedCellType,
    otherConvertedCellType: String? = null,
  ): LocationDTO {
    var locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = createLinkedTransaction(
      prisonId = locationToConvert.prisonId,
      TransactionType.CELL_CONVERTION_TO_ROOM,
      "Converted Location to room ${locationToConvert.getKey()}",
    )

    if (locationToConvert.isNonResType()) {
      locationRepository.updateLocationType(locationToConvert.id!!, LocationType.CELL.name)
      locationRepository.updateResidentialHousingType(
        locationToConvert.id!!,
        ResidentialHousingType.OTHER_USE.name,
        AccommodationType.OTHER_NON_RESIDENTIAL.name,
      )
      entityManager.flush()
      entityManager.clear()
      locationToConvert = residentialLocationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }
    }

    checkForPrisonersInLocation(locationToConvert)

    if (locationToConvert is Cell) {
      locationToConvert.convertToNonResidentialCell(
        convertedCellType = convertedCellType,
        otherConvertedCellType = otherConvertedCellType,
        userOrSystemInContext = getUsername(),
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    trackLocationUpdate(locationToConvert, "Converted Location to non-residential cell")
    return locationToConvert.toDto(includeParent = true).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun convertToCell(
    id: UUID,
    accommodationType: AllowedAccommodationTypeForConversion,
    specialistCellTypes: Set<SpecialistCellType>? = null,
    maxCapacity: Int = 0,
    workingCapacity: Int = 0,
    usedForTypes: List<UsedForType>? = null,
  ): LocationDTO {
    val locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = createLinkedTransaction(
      prisonId = locationToConvert.prisonId,
      TransactionType.ROOM_CONVERTION_TO_CELL,
      "Converted non-residential cell to residential cell ${locationToConvert.getKey()}",
    )

    if (locationToConvert is Cell && locationToConvert.isConvertedCell()) {
      locationToConvert.convertToCell(
        accommodationType = accommodationType,
        usedForTypes = usedForTypes,
        specialistCellTypes = specialistCellTypes,
        maxCapacity = maxCapacity,
        workingCapacity = workingCapacity,
        userOrSystemInContext = getUsername(),
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    trackLocationUpdate(locationToConvert, "Converted non-residential cell to residential cell")
    return locationToConvert.toDto(includeParent = true).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun buildNewPathHierarchy(parentLocation: Location?, code: String) = if (parentLocation != null) {
    parentLocation.getPathHierarchy() + "-"
  } else {
    ""
  } + code

  private fun checkParentValid(
    parentLocation: Location?,
    code: String,
    prisonId: String,
  ) {
    val pathHierarchy = buildNewPathHierarchy(parentLocation, code)

    locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, pathHierarchy)
      ?.let { throw LocationAlreadyExistsException("$prisonId-$pathHierarchy") }
  }

  private fun getParentLocation(parentId: UUID?): Location? = parentId?.let {
    locationRepository.findById(parentId).getOrNull()
      ?: throw LocationNotFoundException(it.toString())
  }

  fun getResidentialLocations(
    prisonId: String,
    parentLocationId: UUID? = null,
    parentPathHierarchy: String? = null,
  ): ResidentialSummary {
    val currentLocation =
      if (parentLocationId != null) {
        residentialLocationRepository.findById(parentLocationId).getOrNull()
          ?: throw LocationNotFoundException(parentLocationId.toString())
      } else if (parentPathHierarchy != null) {
        residentialLocationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, parentPathHierarchy)
          ?: throw LocationNotFoundException("$prisonId-$parentPathHierarchy")
      } else {
        null
      }

    val id = currentLocation?.id
    val locations = (
      if (id != null) {
        residentialLocationRepository.findAllByPrisonIdAndParentId(prisonId, id)
      } else {
        residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
      }
      )
      .filter { !it.isPermanentlyDeactivated() }
      .filter { it.isLocationShownOnResidentialSummary() }
      .map { it.toDto(countInactiveCells = true, countCells = true) }
      .sortedWith(NaturalOrderComparator())

    val subLocationTypes = calculateSubLocationDescription(locations)
    return ResidentialSummary(
      topLevelLocationType = if (currentLocation == null) {
        subLocationTypes ?: "Wings"
      } else {
        currentLocation.getHierarchy()[0].type.description + "s"
      },
      prisonSummary = if (id == null) {
        val prisonDetails = prisonService.lookupPrisonDetails(prisonId) ?: throw PrisonNotFoundException(prisonId)

        PrisonSummary(
          prisonName = prisonDetails.prisonName,
          workingCapacity = locations.sumOf { it.capacity?.workingCapacity ?: 0 },
          maxCapacity = locations.sumOf { it.capacity?.maxCapacity ?: 0 },
          numberOfCellLocations = locations.sumOf { it.numberOfCellLocations ?: 0 },
          signedOperationalCapacity = signedOperationCapacityRepository.findById(prisonId).getOrNull()?.signedOperationCapacity
            ?: 0,
        )
      } else {
        null
      },
      locationHierarchy = currentLocation?.getHierarchy(),
      parentLocation = currentLocation?.toDto(countInactiveCells = true, useHistoryForUpdate = true, countCells = true),
      subLocations = locations,
      subLocationName = subLocationTypes,
    )
  }

  private fun calculateSubLocationDescription(locations: List<LocationDTO>): String? {
    if (locations.isEmpty()) return null
    val mostCommonType = mostCommon(locations.map { it.locationType })
    return (mostCommonType?.description) + "s"
  }

  fun <T> mostCommon(list: List<T>): T? = list.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

  fun getArchivedLocations(prisonId: String): List<LocationDTO> = residentialLocationRepository.findAllByPrisonIdAndArchivedIsTrue(prisonId).map { it.toDto() }
    .sortedWith(NaturalOrderComparator())

  fun getResidentialInactiveLocations(prisonId: String, parentLocationId: UUID?): List<LocationDTO> {
    val startLocation = parentLocationId?.let {
      residentialLocationRepository.findById(parentLocationId).getOrNull() ?: throw LocationNotFoundException(
        parentLocationId.toString(),
      )
    }

    return (
      startLocation?.cellLocations() ?: cellLocationRepository.findAllByPrisonIdAndStatus(prisonId, LocationStatus.INACTIVE)
      )
      .filter { it.isTemporarilyDeactivated() }
      .map { it.toDto() }
      .sortedWith(NaturalOrderComparator())
  }

  private fun checkForPrisonersInLocation(location: Location) {
    val locationsWithPrisoners = prisonerLocationService.prisonersInLocations(location).groupBy { it.cellLocation!! }
    if (locationsWithPrisoners.isNotEmpty()) {
      throw LocationContainsPrisonersException(locationsWithPrisoners)
    }
  }

  fun getCellsWithCapacity(
    prisonId: String,
    locationId: UUID? = null,
    groupName: String? = null,
    specialistCellType: SpecialistCellType? = null,
    includePrisonerInformation: Boolean = false,
  ): List<CellWithSpecialistCellTypes> {
    val cellsToFilter = if (locationId != null) {
      residentialLocationRepository.findOneByPrisonIdAndId(prisonId, locationId)?.cellLocations()
        ?: throw LocationNotFoundException(locationId.toString())
    } else {
      cellLocationRepository.findAllByPrisonIdAndStatus(prisonId, LocationStatus.ACTIVE)
    }

    val cellsWithCapacity = cellsInGroup(
      prisonId = prisonId,
      groupName = groupName,
      cellsToFilter = cellsToFilter,
    )
      .filter { cell -> cell.isActiveAndAllParentsActive() && cell.isCertified() }
      .filter { cell ->
        specialistCellType == null || specialistCellType in cell.specialistCellTypes.map { it.specialistCellType }
      }
    val mapOfOccupancy = prisonerLocationService.prisonersInLocations(prisonId, cellsWithCapacity)
      .groupBy { it.cellLocation }

    return cellsWithCapacity.map { cell ->
      CellWithSpecialistCellTypes(
        id = cell.id!!,
        prisonId = cell.prisonId,
        pathHierarchy = cell.getPathHierarchy(),
        maxCapacity = cell.getMaxCapacity() ?: 0,
        workingCapacity = cell.getWorkingCapacity() ?: 0,
        localName = cell.localName,
        specialistCellTypes = cell.specialistCellTypes.map {
          CellWithSpecialistCellTypes.CellType(
            it.specialistCellType,
            it.specialistCellType.description,
          )
        },
        legacyAttributes = cell.attributes.filter { it.attributeType == ResidentialAttributeType.LOCATION_ATTRIBUTE }
          .map {
            CellWithSpecialistCellTypes.ResidentialLocationAttribute(
              it.attributeValue,
              it.attributeValue.description,
            )
          },
        noOfOccupants = mapOfOccupancy[cell.getPathHierarchy()]?.size ?: 0,
        prisonersInCell = if (includePrisonerInformation) {
          mapOfOccupancy[cell.getPathHierarchy()]
        } else {
          null
        },
      )
    }.filter {
      it.hasSpace()
    }.sortedBy { it.pathHierarchy }
  }

  fun getCellAttributes(id: UUID): List<CellAttributes> {
    val cell = cellLocationRepository.findById(id).getOrNull()
      ?: throw LocationNotFoundException(id.toString())

    return if (activePrisonService.isActivePrison(cell.prisonId)) {
      cell.specialistCellTypes
        .map {
          CellAttributes(
            it.specialistCellType,
            it.specialistCellType.description,
          )
        }
    } else {
      cell.attributes.filter { it.attributeType == ResidentialAttributeType.LOCATION_ATTRIBUTE }
        .map {
          CellAttributes(
            it.attributeValue,
            it.attributeValue.description,
          )
        }
    }
  }

  fun getUsedForTypesForPrison(prisonId: String): List<UsedForType> {
    val prisonDetails = prisonService.lookupPrisonDetails(prisonId) ?: throw PrisonNotFoundException(prisonId)
    return UsedForType.entries.filter { it.isStandard() || (prisonDetails.female && it.femaleOnly) || (prisonDetails.lthse && it.secureEstateOnly) }
  }

  fun findAllByPrisonIdTopParentAndLocalName(prisonId: String, localName: String, parentLocationId: UUID? = null): List<LocationDTO> {
    val foundLocations =
      parentLocationId?.let {
        locationRepository.findAllByPrisonIdAndParentIdAndLocalName(prisonId = prisonId, parentId = parentLocationId, localName = localName)
      } ?: locationRepository.findAllByPrisonIdAndParentIsNullAndLocalName(prisonId = prisonId, localName = localName)

    return foundLocations
      .filter { !it.isPermanentlyDeactivated() }
      .map { it.toDto() }
  }

  fun findByPrisonIdTopParentAndLocalName(prisonId: String, localName: String, parentLocationId: UUID? = null): LocationDTO = findAllByPrisonIdTopParentAndLocalName(
    prisonId = prisonId,
    localName = localName,
    parentLocationId = parentLocationId,
  ).firstOrNull() ?: throw LocationNotFoundException("$prisonId-$parentLocationId-$localName")
}

fun buildEventsToPublishOnUpdate(results: UpdateLocationResult): () -> Map<InternalLocationDomainEventType, List<LocationDTO>> {
  val locationsChanged = if (results.otherParentLocationChanged != null) {
    listOf(results.location, results.otherParentLocationChanged)
  } else {
    listOf(results.location)
  }

  return {
    mapOf(
      InternalLocationDomainEventType.LOCATION_AMENDED to locationsChanged,
    )
  }
}

@Schema(description = "Residential Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResidentialSummary(
  @Schema(description = "Prison summary for top level view", required = false)
  val prisonSummary: PrisonSummary? = null,
  @Schema(description = "The top level type of locations", required = true, example = "Wings")
  val topLevelLocationType: String,
  @Schema(
    description = "The description of the type of sub locations most common",
    required = false,
    examples = ["Wings", "Landings", "Spurs", "Cells"],
  )
  val subLocationName: String? = null,
  @Schema(description = "Parent locations, top to bottom", required = true)
  val locationHierarchy: List<LocationSummary>? = null,
  @Schema(description = "The current parent location (e.g Wing or Landing) details")
  val parentLocation: LocationDTO? = null,
  @Schema(description = "All residential locations under this parent")
  val subLocations: List<LocationDTO>,
)

@Schema(description = "Prison Summary Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonSummary(
  @Schema(description = "Prison name")
  val prisonName: String,
  @Schema(description = "Prison working capacity")
  val workingCapacity: Int,
  @Schema(description = "Prison signed operational capacity")
  val signedOperationalCapacity: Int,
  @Schema(description = "Prison max capacity")
  val maxCapacity: Int,
  @Schema(description = "Total number of non-structural locations  e.g. cells and rooms")
  val numberOfCellLocations: Int,
)

data class UpdatedSummary(
  val codeChanged: Boolean = false,
  val oldParent: Location? = null,
  val parentChanged: Boolean = false,
)

data class UpdateLocationResult(
  val location: LocationDTO,
  val otherParentLocationChanged: LocationDTO? = null,
)

data class CapacityUpdateResult(
  val updatedLocations: Map<InternalLocationDomainEventType, List<LocationDTO>>,
  val audit: Map<String, List<CapacityChanges>>,
)

@Schema(description = "Capacity change audit")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CapacityChanges(
  @Schema(description = "Location reference", example = "MDI-1-1-001")
  val key: String,
  @Schema(description = "textual description of the changes", example = "Working capacity from 2 ==> 1")
  val message: String,
  @Schema(description = "Attribute changed in the update", example = "workingCapacity")
  val type: String? = null,
  @Schema(description = "Old value of this attribute", example = "2")
  val previousValue: Int? = null,
  @Schema(description = "New value of this attribute", example = "1")
  val newValue: Int? = null,
)
