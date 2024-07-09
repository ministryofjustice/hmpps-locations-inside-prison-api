package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationHistoryRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellWithSpecialistCellTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationContainsPrisonersException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateLocationResult
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class LocationService(
  private val locationRepository: LocationRepository,
  private val nonResidentialLocationRepository: NonResidentialLocationRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val signedOperationCapacityRepository: PrisonSignedOperationCapacityRepository,
  private val locationHistoryRepository: LocationHistoryRepository,
  private val cellLocationRepository: CellLocationRepository,
  private val prisonerLocationService: PrisonerLocationService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationFacade: AuthenticationFacade,
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getLocationById(id: UUID, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? {
    val toDto = locationRepository.findById(id).getOrNull()?.toDto(includeChildren = includeChildren, includeHistory = includeHistory)
    return toDto
  }

  fun getLocationByPrison(prisonId: String): List<LocationDTO> =
    locationRepository.findAllByPrisonIdOrderByPathHierarchy(prisonId)
      .filter { !it.isPermanentlyDeactivated() }
      .map {
        it.toDto()
      }
      .sortedBy { it.getKey() }

  fun getLocationGroupsForPrison(prisonId: String): List<LocationGroupDto> {
    val groups = locationGroupFromPropertiesService.getLocationGroups(prisonId)
    return groups.ifEmpty {
      residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
        .filter { it.isActiveAndAllParentsActive() && it.isWingLandingSpur() }
        .map {
          it.toLocationGroupDto()
        }
        .sortedWith(NaturalOrderComparator())
    }
  }

  fun getCellLocationsForGroup(prisonId: String, groupName: String): List<LocationDTO> =
    cellsInGroup(prisonId, groupName, cellLocationRepository.findAllByPrisonIdAndActive(prisonId, true))
      .toMutableList()
      .map { it.toDto() }
      .sortedWith(NaturalOrderComparator())

  private fun cellsInGroup(
    prisonId: String,
    groupName: String?,
    cellsToFilter: List<Cell>,
  ): List<Cell> {
    return if (groupName != null) {
      cellsToFilter.filter(locationGroupFromPropertiesService.locationGroupFilter(prisonId, groupName)::test)
    } else {
      cellsToFilter
    }
  }

  fun getLocationsByPrisonAndNonResidentialUsageType(prisonId: String, usageType: NonResidentialUsageType): List<LocationDTO> =
    nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(prisonId, usageType)
      .map {
        it.toDto()
      }

  fun getLocationByKey(key: String, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? {
    if (!key.contains("-")) throw LocationNotFoundException(key)

    val (prisonId, code) = key.split("-", limit = 2)
    return locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, code)?.toDto(includeChildren = includeChildren, includeHistory = includeHistory)
  }

  fun getLocationsByKeys(keys: List<String>): List<LocationDTO> =
    locationRepository.findAllByKeys(keys)
      .map { it.toDto() }
      .sortedBy { it.getKey() }

  fun getLocations(pageable: Pageable = PageRequest.of(0, 20, Sort.by("id"))): Page<LegacyLocation> {
    return locationRepository.findAll(pageable).map(Location::toLegacyDto)
  }
  fun getLocationByPrisonAndLocationType(prisonId: String, locationType: LocationType): List<LocationDTO> =
    locationRepository.findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId, locationType)
      .filter { it.isActive() }
      .map {
        it.toDto()
      }
      .sortedBy { it.getKey() }

  @Transactional
  fun createResidentialLocation(request: CreateResidentialLocationRequest): LocationDTO {
    val parentLocation = getParentLocation(request.parentId)

    checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with existing location

    val locationToCreate = request.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    parentLocation?.let { locationToCreate.setParent(it) }

    val capacityChanged = request.isCell() && request.capacity != null

    val location = locationRepository.save(locationToCreate).toDto(includeParent = capacityChanged)

    log.info("Created Residential Location [${location.getKey()}]")
    telemetryClient.trackEvent(
      "Created Residential Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )

    return location
  }

  @Transactional
  fun createNonResidentialLocation(request: CreateNonResidentialLocationRequest): LocationDTO {
    val parentLocation = getParentLocation(request.parentId)

    checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with existing location

    val locationToCreate = request.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    parentLocation?.let { locationToCreate.setParent(it) }

    val usageChanged = request.usage != null

    val location = locationRepository.save(locationToCreate).toDto(includeParent = usageChanged)

    log.info("Created Non-Residential Location [${location.getKey()}]")
    telemetryClient.trackEvent(
      "Created Non-Residential Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )

    return location
  }

  @Transactional
  fun createWing(createWingRequest: CreateWingRequest): LocationDTO {
    locationRepository.findOneByPrisonIdAndPathHierarchy(createWingRequest.prisonId, createWingRequest.wingCode)
      ?.let { throw LocationAlreadyExistsException("${createWingRequest.prisonId}-${createWingRequest.wingCode}") }

    val wing = createWingRequest.toEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    return locationRepository.save(wing).toDto(includeChildren = true, includeNonResidential = false)
  }

  @Transactional
  fun updateResidentialLocation(id: UUID, patchLocationRequest: PatchResidentialLocationRequest): UpdateLocationResult {
    val residentialLocation = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val (codeChanged, oldParent, parentChanged) = updateLocation(residentialLocation, patchLocationRequest)

    residentialLocation.update(patchLocationRequest, authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Updated Residential Location [$residentialLocation]")
    telemetryClient.trackEvent(
      "Updated Residential Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to residentialLocation.prisonId,
        "path" to residentialLocation.getPathHierarchy(),
        "codeChanged" to "$codeChanged",
        "parentChanged" to "$parentChanged",
      ),
      null,
    )

    return UpdateLocationResult(
      residentialLocation.toDto(includeChildren = codeChanged || parentChanged, includeParent = parentChanged, includeNonResidential = false),
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  @Transactional
  fun updateResidentialLocationUsedForTypes(id: UUID, usedFor: Set<UsedForType>): LocationDTO {
    val residentialLocation = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    residentialLocation.updateCellUsedFor(
      usedFor,
      authenticationFacade.getUserOrSystemInContext(),
      clock,
    )

    log.info("Updated Used for types for below Location [$residentialLocation.getKey()]")
    telemetryClient.trackEvent(
      "Updated Used For Type below Residential Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to residentialLocation.prisonId,
        "path" to residentialLocation.getPathHierarchy(),
      ),
      null,
    )
    return residentialLocation.toDto(includeChildren = true, includeNonResidential = false)
  }

  @Transactional
  fun updateNonResidentialLocation(id: UUID, patchLocationRequest: PatchNonResidentialLocationRequest): UpdateLocationResult {
    val nonResLocation = nonResidentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val (codeChanged, oldParent, parentChanged) = updateLocation(nonResLocation, patchLocationRequest)

    nonResLocation.update(patchLocationRequest, authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Updated Non Residential Location [$nonResLocation]")
    telemetryClient.trackEvent(
      "Updated Non Residential Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to nonResLocation.prisonId,
        "path" to nonResLocation.getPathHierarchy(),
        "codeChanged" to "$codeChanged",
        "parentChanged" to "$parentChanged",
      ),
      null,
    )

    return UpdateLocationResult(
      nonResLocation.toDto(includeChildren = codeChanged || parentChanged, includeParent = parentChanged),
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  private fun updateLocation(
    locationToUpdate: Location,
    patchLocationRequest: PatchLocationRequest,
  ): UpdatedSummary {
    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw ValidationException("Cannot update a permanently inactive location")
    }

    val codeChanged = patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getCode()
    val oldParent = locationToUpdate.getParent()
    val parentChanged = patchLocationRequest.parentId != null && patchLocationRequest.parentId != oldParent?.id

    if (parentChanged) {
      locationToUpdate.addHistory(
        LocationAttribute.PARENT_LOCATION,
        oldParent?.id?.toString(),
        patchLocationRequest.parentId?.toString(),
        authenticationFacade.getUserOrSystemInContext(),
        LocalDateTime.now(clock),
      )
    }

    if (codeChanged || parentChanged) {
      val newCode = patchLocationRequest.code ?: locationToUpdate.getCode()
      val theParent = patchLocationRequest.parentId?.let {
        locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString())
      } ?: oldParent
      checkParentValid(theParent, newCode, locationToUpdate.prisonId)

      if (parentChanged && theParent?.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      theParent?.let { locationToUpdate.setParent(it) }
    }
    return UpdatedSummary(codeChanged = codeChanged, oldParent = oldParent, parentChanged = parentChanged)
  }

  @Transactional
  fun updateCellCapacity(id: UUID, maxCapacity: Int, workingCapacity: Int): LocationDTO {
    val locCapChange = cellLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locCapChange.isPermanentlyDeactivated()) {
      throw ValidationException("Cannot change the capacity a permanently deactivated location")
    }

    val prisoners = prisonerLocationService.prisonersInLocations(locCapChange)
    if (maxCapacity < prisoners.size) {
      throw CapacityException(locCapChange.getKey(), "Max capacity ($maxCapacity) cannot be decreased below current cell occupancy (${prisoners.size})")
    }

    locCapChange.setCapacity(
      maxCapacity = maxCapacity,
      workingCapacity = workingCapacity,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )
    log.info("Capacity updated max capacity = $maxCapacity and working capacity = $workingCapacity")

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
    return locCapChange.toDto(includeParent = true, includeNonResidential = false)
  }

  @Transactional
  fun updateLocation(id: UUID, updateLocationRequest: UpdateLocationRequest): LocationDTO {
    val location = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (location.isPermanentlyDeactivated()) {
      throw ValidationException("Cannot change the local name of a permanently deactivated location")
    }

    location.updateLocalName(
      localName = updateLocationRequest.localName,
      userOrSystemInContext = updateLocationRequest.updatedBy,
      clock = clock,
    )

    location.updateComments(
      comments = updateLocationRequest.comments,
      userOrSystemInContext = updateLocationRequest.updatedBy,
      clock = clock,
    )

    log.info("Location updated [${location.getKey()}")

    telemetryClient.trackEvent(
      "Location updated",
      mapOf(
        "id" to id.toString(),
        "key" to location.getKey(),
      ),
      null,
    )
    return location.toDto()
  }

  @Transactional
  fun deactivateLocation(
    id: UUID,
    deactivatedReason: DeactivatedReason,
    proposedReactivationDate: LocalDate? = null,
    planetFmReference: String? = null,
  ): LocationDTO {
    val locationToDeactivate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locationToDeactivate.isTemporarilyDeactivated()) {
      throw ValidationException("Cannot deactivate already deactivated location")
    }

    checkForPrisonersInLocation(locationToDeactivate)

    locationToDeactivate.temporarilyDeactivate(
      deactivatedReason = deactivatedReason,
      deactivatedDate = LocalDateTime.now(clock),
      proposedReactivationDate = proposedReactivationDate,
      planetFmReference = planetFmReference,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

    telemetryClient.trackEvent(
      "Temporarily Deactivated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToDeactivate.prisonId,
        "path" to locationToDeactivate.getPathHierarchy(),
      ),
      null,
    )

    return locationToDeactivate.toDto(includeParent = true, includeChildren = true)
  }

  @Transactional
  fun updateDeactivatedDetails(
    id: UUID,
    deactivatedReason: DeactivatedReason,
    proposedReactivationDate: LocalDate? = null,
    planetFmReference: String? = null,
  ): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locationToUpdate.isTemporarilyDeactivated()) {
      locationToUpdate.updateDeactivatedDetails(
        deactivatedReason = deactivatedReason,
        proposedReactivationDate = proposedReactivationDate,
        planetFmReference = planetFmReference,
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
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
    return locationToUpdate.toDto()
  }

  @Transactional
  fun permanentlyDeactivateLocation(
    id: UUID,
    reasonForPermanentDeactivation: String,
  ): LocationDTO {
    val locationToArchive = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locationToArchive.isPermanentlyDeactivated()) {
      throw ValidationException("Cannot deactivate already permanently deactivated location")
    }

    checkForPrisonersInLocation(locationToArchive)

    locationToArchive.permanentlyDeactivate(
      deactivatedDate = LocalDateTime.now(clock),
      reason = reasonForPermanentDeactivation,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

    telemetryClient.trackEvent(
      "Permanently Deactivated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToArchive.prisonId,
        "path" to locationToArchive.getPathHierarchy(),
      ),
      null,
    )

    return locationToArchive.toDto(includeParent = true, includeChildren = true)
  }

  @Transactional
  fun reactivateLocation(id: UUID, reactivateSubLocations: Boolean = false): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationToUpdate.reactivate(authenticationFacade.getUserOrSystemInContext(), clock)

    if (reactivateSubLocations) {
      locationToUpdate.findSubLocations().forEach { location ->
        location.reactivate(
          userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
          clock = clock,
        )
      }
    }

    telemetryClient.trackEvent(
      "Re-activated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto(includeParent = true, includeChildren = reactivateSubLocations)
  }

  @Transactional
  fun convertToNonResidentialCell(id: UUID, convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null): LocationDTO {
    val locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    checkForPrisonersInLocation(locationToConvert)

    if (locationToConvert is Cell) {
      locationToConvert.convertToNonResidentialCell(
        convertedCellType = convertedCellType,
        otherConvertedCellType = otherConvertedCellType,
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    telemetryClient.trackEvent(
      "Converted Location to non-residential cell",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToConvert.prisonId,
        "path" to locationToConvert.getPathHierarchy(),
      ),
      null,
    )
    return locationToConvert.toDto(includeParent = true)
  }

  @Transactional
  fun convertToCell(id: UUID, accommodationType: AccommodationType, specialistCellType: SpecialistCellType?, maxCapacity: Int = 0, workingCapacity: Int = 0, usedForTypes: List<UsedForType>? = null): LocationDTO {
    val locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locationToConvert is Cell && locationToConvert.isConvertedCell()) {
      locationToConvert.convertToCell(
        accommodationType = accommodationType,
        usedForTypes = usedForTypes,
        specialistCellType = specialistCellType,
        maxCapacity = maxCapacity,
        workingCapacity = workingCapacity,
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    telemetryClient.trackEvent(
      "Converted non-residential cell to residential cell",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToConvert.prisonId,
        "path" to locationToConvert.getPathHierarchy(),
      ),
      null,
    )
    return locationToConvert.toDto(includeParent = true)
  }

  private fun buildNewPathHierarchy(parentLocation: Location?, code: String) =
    if (parentLocation != null) {
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

  private fun getParentLocation(parentId: UUID?): Location? =
    parentId?.let {
      locationRepository.findById(parentId).getOrNull()
        ?: throw LocationNotFoundException(it.toString())
    }

  fun getResidentialLocations(
    prisonId: String,
    parentLocationId: UUID? = null,
    parentPathHierarchy: String? = null,
    returnLatestHistory: Boolean = false,
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
      .filter { it.isCell() || it.isLocationShownOnResidentialSummary() }
      .map { it.toDto(countInactiveCells = true) }
      .sortedWith(NaturalOrderComparator())

    val latestHistory = if (id != null && returnLatestHistory) {
      locationHistoryRepository.findTop10ByLocationIdOrderByAmendedDateDesc(id).map { it.toDto() }
    } else {
      null
    }
    val subLocationTypes = calculateSubLocationDescription(locations)
    return ResidentialSummary(
      topLevelLocationType = if (currentLocation == null) {
        subLocationTypes ?: "Wings"
      } else {
        currentLocation.getHierarchy()[0].type.description + "s"
      },
      prisonSummary = if (id == null) {
        PrisonSummary(
          workingCapacity = locations.sumOf { it.capacity?.workingCapacity ?: 0 },
          maxCapacity = locations.sumOf { it.capacity?.maxCapacity ?: 0 },
          signedOperationalCapacity = signedOperationCapacityRepository.findOneByPrisonId(prisonId)?.signedOperationCapacity ?: 0,
        )
      } else {
        null
      },
      locationHierarchy = currentLocation?.getHierarchy(),
      parentLocation = currentLocation?.toDto(countInactiveCells = true),
      latestHistory = latestHistory,
      subLocations = locations,
      subLocationName = subLocationTypes,
    )
  }

  private fun calculateSubLocationDescription(locations: List<LocationDTO>): String? {
    if (locations.isEmpty()) return null
    val mostCommonType = mostCommon(locations.map { it.locationType })
    return (mostCommonType?.description) + "s"
  }

  fun <T> mostCommon(list: List<T>): T? {
    return list.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
  }

  fun getArchivedLocations(prisonId: String): List<LocationDTO> = residentialLocationRepository.findAllByPrisonIdAndArchivedIsTrue(prisonId).map { it.toDto() }.sortedWith(NaturalOrderComparator())

  fun getResidentialInactiveLocations(prisonId: String, parentLocationId: UUID?): List<LocationDTO> {
    val startLocation = parentLocationId?.let {
      residentialLocationRepository.findById(parentLocationId).getOrNull() ?: throw LocationNotFoundException(
        parentLocationId.toString(),
      )
    }

    return (
      startLocation?.findAllLeafLocations() ?: cellLocationRepository.findAllByPrisonIdAndActive(prisonId, false)
      )
      .filter { it.isTemporarilyDeactivated() }
      .map { it.toDto() }
      .sortedWith(NaturalOrderComparator())
  }

  private fun checkForPrisonersInLocation(location: Location) {
    val locationsWithPrisoners = prisonerLocationService.prisonersInLocations(location).groupBy { it.cellLocation }
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
      cellLocationRepository.findAllByPrisonIdAndActive(prisonId, true)
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
        specialistCellTypes = cell.specialistCellTypes.map { CellWithSpecialistCellTypes.CellType(it.specialistCellType, it.specialistCellType.description) },
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
}

@Schema(description = "Residential Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResidentialSummary(
  @Schema(description = "Prison summary for top level view", required = false)
  val prisonSummary: PrisonSummary? = null,
  @Schema(description = "The top level type of locations", required = true, example = "Wings")
  val topLevelLocationType: String,
  @Schema(description = "The description of the type of sub locations most common", required = false, examples = ["Wings", "Landings", "Spurs", "Cells"])
  val subLocationName: String? = null,
  @Schema(description = "Parent locations, top to bottom", required = true)
  val locationHierarchy: List<LocationSummary>? = null,
  @Schema(description = "The current parent location (e.g Wing or Landing) details")
  val parentLocation: LocationDTO? = null,
  @Schema(description = "All residential locations under this parent")
  val subLocations: List<LocationDTO>,
  @Schema(description = "The latest history for this location")
  val latestHistory: List<ChangeHistory>? = null,
)

@Schema(description = "Prison Summary Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonSummary(
  @Schema(description = "Prison working capacity")
  val workingCapacity: Int,
  @Schema(description = "Prison signed operational capacity")
  val signedOperationalCapacity: Int,
  @Schema(description = "Prison max capacity")
  val maxCapacity: Int,
)

data class UpdatedSummary(
  val codeChanged: Boolean = false,
  val oldParent: Location? = null,
  val parentChanged: Boolean = false,
)
