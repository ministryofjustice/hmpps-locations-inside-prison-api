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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationPrefixDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.AlreadyDeactivatedLocationException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellWithSpecialistCellTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DuplicateLocalNameForSamePrisonException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationContainsPrisonersException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationPrefixNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReasonForDeactivationMustBeProvidedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateCapacityRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class LocationService(
  private val locationRepository: LocationRepository,
  private val nonResidentialLocationRepository: NonResidentialLocationRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val signedOperationCapacityRepository: PrisonSignedOperationCapacityRepository,
  private val cellLocationRepository: CellLocationRepository,
  private val entityManager: EntityManager,
  private val prisonerLocationService: PrisonerLocationService,
  private val prisonService: PrisonService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationFacade: AuthenticationFacade,
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService,
  @Qualifier("residentialGroups") private val groupsProperties: Properties,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getLocationById(id: UUID, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? {
    val toDto = locationRepository.findById(id).getOrNull()?.toDto(
      includeChildren = includeChildren,
      includeHistory = includeHistory,
    )
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
        .filter { it.isActiveAndAllParentsActive() && it.isStructural() }
        .map {
          it.toLocationGroupDto()
        }
        .sortedWith(NaturalOrderComparator())
    }
  }

  fun getLocationPrefixFromGroup(prisonId: String, group: String): LocationPrefixDto {
    val agencyGroupKey = "${prisonId}_$group"

    val pattern = groupsProperties.getProperty(agencyGroupKey)
      ?: throw LocationPrefixNotFoundException(agencyGroupKey)

    val locationPrefix = pattern
      .replace(".", "")
      .replace("+", "")

    return LocationPrefixDto(locationPrefix)
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
      cellsToFilter.filter(locationGroupFilter(prisonId, groupName)::test)
    } else {
      cellsToFilter
    }
  }

  private fun locationGroupFilter(prisonId: String, groupName: String): Predicate<Location> {
    return try {
      locationGroupFromPropertiesService.locationGroupFilter(prisonId, groupName)
    } catch (e: EntityNotFoundException) {
      fallBackLocationGroupFilter(groupName)
    }
  }

  private fun fallBackLocationGroupFilter(groupName: String): Predicate<Location> {
    val prefixToMatch = "${groupName.replace('_', '-')}-"
    return Predicate { it.getPathHierarchy().startsWith(prefixToMatch) }
  }

  fun getLocationsByPrisonAndNonResidentialUsageType(
    prisonId: String,
    usageType: NonResidentialUsageType,
  ): List<LocationDTO> =
    nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(prisonId, usageType)
      .map {
        it.toDto()
      }

  fun getLocationByKey(key: String, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? {
    return locationRepository.findOneByKey(key)?.toDto(
      includeChildren = includeChildren,
      includeHistory = includeHistory,
    )
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

    val createdLocation = locationRepository.save(locationToCreate)

    log.info("Created Residential Location [${createdLocation.getKey()}]")
    trackLocationUpdate(createdLocation, "Created Residential Location")

    return createdLocation.toDto(includeParent = capacityChanged)
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

    val createdLocation = locationRepository.save(locationToCreate)

    log.info("Created Non-Residential Location [${createdLocation.getKey()}]")
    trackLocationUpdate(createdLocation, "Created Non-Residential Location")

    return createdLocation.toDto(includeParent = usageChanged)
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
    val residentialLocation =
      residentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }
    return patchLocation(residentialLocation, patchLocationRequest).also {
      trackLocationUpdate(it.location)
    }
  }

  @Transactional
  fun updateResidentialLocation(
    key: String,
    patchLocationRequest: PatchResidentialLocationRequest,
  ): UpdateLocationResult {
    val residentialLocation = residentialLocationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)
    return patchLocation(residentialLocation, patchLocationRequest).also {
      trackLocationUpdate(it.location)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    id: UUID,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }
    return patchLocation(nonResLocation, patchLocationRequest).also {
      trackLocationUpdate(it.location)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    key: String,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation = nonResidentialLocationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)
    return patchLocation(nonResLocation, patchLocationRequest).also {
      trackLocationUpdate(it.location)
    }
  }

  private fun patchLocation(
    location: Location,
    patchLocationRequest: PatchLocationRequest,
  ): UpdateLocationResult {
    val (codeChanged, oldParent, parentChanged) = updateCoreLocationDetails(location, patchLocationRequest)

    location.update(patchLocationRequest, authenticationFacade.getUserOrSystemInContext(), clock)

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

    residentialLocation.updateCellUsedFor(
      usedFor,
      authenticationFacade.getUserOrSystemInContext(),
      clock,
    )

    log.info("Updated Used for types for below Location [$residentialLocation.getKey()]")
    trackLocationUpdate(residentialLocation, "Updated Used For Type below Residential Location")

    return residentialLocation.toDto(includeChildren = true, includeNonResidential = false)
  }

  private fun updateCoreLocationDetails(
    locationToUpdate: Location,
    patchLocationRequest: PatchLocationRequest,
  ): UpdatedSummary {
    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(locationToUpdate.getKey())
    }

    val codeChanged = patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getCode()
    val oldParent = locationToUpdate.getParent()
    val parentChanged = (patchLocationRequest.parentId != null && patchLocationRequest.parentId != oldParent?.id) ||
      (patchLocationRequest.parentLocationKey != null && patchLocationRequest.parentLocationKey != oldParent?.getKey())

    if (codeChanged || parentChanged) {
      val newCode = patchLocationRequest.code ?: locationToUpdate.getCode()
      val theParent = patchLocationRequest.parentId?.let {
        locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString())
      } ?: patchLocationRequest.parentLocationKey?.let {
        locationRepository.findOneByKey(it) ?: throw LocationNotFoundException(it)
      } ?: oldParent
      checkParentValid(theParent, newCode, locationToUpdate.prisonId)

      if (parentChanged && theParent?.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      theParent?.let { locationToUpdate.setParent(it) }

      if (parentChanged) {
        locationToUpdate.addHistory(
          LocationAttribute.PARENT_LOCATION,
          oldParent?.id?.toString(),
          theParent?.id?.toString(),
          authenticationFacade.getUserOrSystemInContext(),
          LocalDateTime.now(clock),
        )
      }
    }
    return UpdatedSummary(codeChanged = codeChanged, oldParent = oldParent, parentChanged = parentChanged)
  }

  @Transactional
  fun updateCellCapacity(id: UUID, maxCapacity: Int, workingCapacity: Int): LocationDTO {
    val locCapChange = cellLocationRepository.findById(id)
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

    locCapChange.setCapacity(
      maxCapacity = maxCapacity,
      workingCapacity = workingCapacity,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

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
  fun updateSpecialistCellTypes(id: UUID, specialistCellTypes: Set<SpecialistCellType>): LocationDTO {
    val cell = cellLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (cell.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(cell.getKey())
    }

    // Check that the workingCapacity is not set to 0 for normal accommodations when removing the specialists cell types
    if (specialistCellTypes.isEmpty() && cell.accommodationType == AccommodationType.NORMAL_ACCOMMODATION && cell.getWorkingCapacity() == 0) {
      throw CapacityException(
        cell.getKey(),
        "Cannot removes specialist cell types for a normal accommodation with a working capacity of 0",
        ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed,
      )
    }

    cell.updateSpecialistCellTypes(
      specialistCellTypes,
      authenticationFacade.getUserOrSystemInContext(),
      clock,
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
    return cell.toDto(includeParent = false, includeNonResidential = false)
  }

  @Transactional
  fun updateLocalName(id: UUID, updateLocationLocalNameRequest: UpdateLocationLocalNameRequest): LocationDTO {
    val location = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (location.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(location.getKey())
    }

    with(updateLocationLocalNameRequest) {
      if (localName != null) {
        if (locationRepository.findAllByPrisonIdAndLocalName(
            prisonId = location.prisonId,
            localName = localName,
          ).any { !it.isPermanentlyDeactivated() && it.id != id }
        ) {
          throw DuplicateLocalNameForSamePrisonException(key = location.getKey(), prisonId = location.prisonId)
        }
      }

      location.updateLocalName(
        localName = localName,
        userOrSystemInContext = updatedBy ?: authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
      )
    }

    log.info("Location local name updated [${location.getKey()}")
    trackLocationUpdate(location, "Location local name updated")

    return location.toDto()
  }

  @Transactional
  fun deactivateLocations(locationsToDeactivate: DeactivateLocationsRequest): Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val deactivatedLocations = mutableSetOf<Location>()

    locationsToDeactivate.locations.forEach { (id, deactivationDetail) ->
      val locationToDeactivate = locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (locationToDeactivate.isTemporarilyDeactivated()) {
        throw AlreadyDeactivatedLocationException(locationToDeactivate.getKey())
      }

      checkForPrisonersInLocation(locationToDeactivate)

      with(deactivationDetail) {
        if (deactivationReason == DeactivatedReason.OTHER && deactivationReasonDescription.isNullOrEmpty()) {
          throw ReasonForDeactivationMustBeProvidedException(locationToDeactivate.getKey())
        }

        if (locationToDeactivate.temporarilyDeactivate(
            deactivatedReason = deactivationReason,
            deactivatedDate = LocalDateTime.now(clock),
            deactivationReasonDescription = deactivationReasonDescription,
            planetFmReference = planetFmReference,
            proposedReactivationDate = proposedReactivationDate,
            userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
            clock = clock,
            deactivatedLocations = deactivatedLocations,
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
    )
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

    if (deactivatedReason == DeactivatedReason.OTHER && deactivationReasonDescription.isNullOrEmpty()) {
      throw ReasonForDeactivationMustBeProvidedException(locationToUpdate.getKey())
    }
    if (locationToUpdate.isTemporarilyDeactivated()) {
      locationToUpdate.updateDeactivatedDetails(
        deactivatedReason = deactivatedReason,
        deactivationReasonDescription = deactivationReasonDescription,
        planetFmReference = planetFmReference,
        proposedReactivationDate = proposedReactivationDate,
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
      throw PermanentlyDeactivatedUpdateNotAllowedException(locationToArchive.getKey())
    }

    checkForPrisonersInLocation(locationToArchive)

    locationToArchive.permanentlyDeactivate(
      deactivatedDate = LocalDateTime.now(clock),
      reason = reasonForPermanentDeactivation,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

    trackLocationUpdate(locationToArchive, "Permanently Deactivated Location")
    return locationToArchive.toDto(includeChildren = true, includeParent = true)
  }

  @Transactional
  fun updateCapacityOfCellLocations(capacitiesToUpdate: UpdateCapacityRequest): CapacityUpdateResult {
    val updatedCapacities = mutableSetOf<ResidentialLocation>()
    val audit = mutableMapOf<String, List<CapacityChanges>>()

    capacitiesToUpdate.locations.forEach { (key, capacityChange) ->
      val changes = mutableListOf<CapacityChanges>()
      val location = residentialLocationRepository.findOneByKey(key)
      if (location != null) {
        if (!location.isPermanentlyDeactivated()) {
          if (location is Cell) {
            with(capacityChange) {
              if (location.getMaxCapacity() != maxCapacity || location.getWorkingCapacity() != workingCapacity) {
                try {
                  val oldWorkingCapacity = location.getWorkingCapacity()
                  val oldMaxCapacity = location.getMaxCapacity()
                  updateCellCapacity(
                    location.id!!,
                    maxCapacity = maxCapacity,
                    workingCapacity = workingCapacity,
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
              if (capacityOfCertifiedCell != null && capacityOfCertifiedCell != location.getCapacityOfCertifiedCell()) {
                val oldCapacityOfCertifiedCell = location.getCapacityOfCertifiedCell()
                location.setCapacityOfCertifiedCell(
                  capacityOfCertifiedCell,
                  authenticationFacade.getUserOrSystemInContext(),
                  clock,
                )
                changes.add(CapacityChanges(key, message = "Baseline CNA from $oldCapacityOfCertifiedCell ==> $capacityOfCertifiedCell", type = "CNA", previousValue = oldCapacityOfCertifiedCell, newValue = capacityOfCertifiedCell))
                updatedCapacities.add(location)
              }
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
      log.info("$key: ${changes.joinToString { "${it.type}: ${it.previousValue} ==> ${it.newValue}" }}")
    }
    val updatedLocationsDto = updatedCapacities.map { it.toDto() }.toSet()

    audit.flatMap { it.value }.forEach { l ->
      var trackMap = mapOf("key" to l.key, "message" to l.message)
      l.type?.let { trackMap = trackMap.plus("${l.type}" to "${l.previousValue} ==> ${l.newValue}") }
      telemetryClient.trackEvent("CAPACITY_CHANGE", trackMap, null)
    }
    return CapacityUpdateResult(
      updatedLocations = mapOf(
        InternalLocationDomainEventType.LOCATION_AMENDED to updatedCapacities.flatMap { changed ->
          changed.getParentLocations().map { it.toDto() }
        }.toSet().plus(updatedLocationsDto).toList(),
      ),
      audit = audit,
    )
  }

  @Transactional
  fun reactivateLocations(locationsToReactivate: ReactivateLocationsRequest): Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val locationsReactivated = mutableSetOf<Location>()

    locationsToReactivate.locations.forEach { (id, reactivationDetail) ->
      val locationToUpdate = locationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (locationToUpdate.isPermanentlyDeactivated()) {
        throw LocationCannotBeReactivatedException("Location [${locationToUpdate.getKey()}] permanently deactivated")
      }

      locationToUpdate.reactivate(
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
        reactivatedLocations = locationsReactivated,
        maxCapacity = reactivationDetail.capacity?.maxCapacity,
        workingCapacity = reactivationDetail.capacity?.workingCapacity,
      )

      if (reactivationDetail.cascadeReactivation) {
        locationToUpdate.findSubLocations().forEach { location ->
          location.reactivate(
            userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
            clock = clock,
            reactivatedLocations = locationsReactivated,
          )
        }
      }
    }

    val reactivatedLocationsDto = locationsReactivated.map { it.toDto() }.toSet()
    locationsReactivated.forEach { trackLocationUpdate(it, "Re-activated Location") }
    return mapOf(
      InternalLocationDomainEventType.LOCATION_AMENDED to locationsReactivated.flatMap { reactivated ->
        reactivated.getParentLocations().map { it.toDto() }
      }.toSet().minus(
        reactivatedLocationsDto,
      ).toList(),
      InternalLocationDomainEventType.LOCATION_REACTIVATED to reactivatedLocationsDto.toList(),
    )
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
    var nonResCellToUpdate = cellLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (!nonResCellToUpdate.isConvertedCell()) {
      throw LocationNotFoundException("${nonResCellToUpdate.getKey()} is not a non-residential cell")
    }

    nonResCellToUpdate.updateNonResidentialCellType(
      convertedCellType = convertedCellType,
      otherConvertedCellType = otherConvertedCellType,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

    trackLocationUpdate(nonResCellToUpdate, "Updated non-residential cell type")
    return nonResCellToUpdate.toDto()
  }

  @Transactional
  fun convertToNonResidentialCell(
    id: UUID,
    convertedCellType: ConvertedCellType,
    otherConvertedCellType: String? = null,
  ): LocationDTO {
    var locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

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
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    trackLocationUpdate(locationToConvert, "Converted Location to non-residential cell")
    return locationToConvert.toDto(includeParent = true)
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

    if (locationToConvert is Cell && locationToConvert.isConvertedCell()) {
      locationToConvert.convertToCell(
        accommodationType = accommodationType,
        usedForTypes = usedForTypes,
        specialistCellTypes = specialistCellTypes,
        maxCapacity = maxCapacity,
        workingCapacity = workingCapacity,
        userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
        clock = clock,
      )
    } else {
      throw LocationNotFoundException(id.toString())
    }

    trackLocationUpdate(locationToConvert, "Converted non-residential cell to residential cell")
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
          signedOperationalCapacity = signedOperationCapacityRepository.findOneByPrisonId(prisonId)?.signedOperationCapacity
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

  fun <T> mostCommon(list: List<T>): T? {
    return list.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
  }

  fun getArchivedLocations(prisonId: String): List<LocationDTO> =
    residentialLocationRepository.findAllByPrisonIdAndArchivedIsTrue(prisonId).map { it.toDto() }
      .sortedWith(NaturalOrderComparator())

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
      cellLocationRepository.findAllByPrisonIdAndActive(prisonId, true)
    }

    val cellsWithCapacity = cellsInGroup(
      prisonId = prisonId,
      groupName = groupName,
      cellsToFilter = cellsToFilter,
    )
      .filter { cell -> cell.isActiveAndAllParentsActive() && cell.isCertified() && !cell.isPermanentlyDeactivated() }
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

  fun getUsedForTypesForPrison(prisonId: String): List<UsedForType> {
    val prisonDetails = prisonService.lookupPrisonDetails(prisonId) ?: throw PrisonNotFoundException(prisonId)
    return UsedForType.entries.filter { it.isStandard() || (prisonDetails.female && it.femaleOnly) || (prisonDetails.lthse && it.secureEstateOnly) }
  }

  fun findByPrisonIdAndLocalName(prisonId: String, localName: String): LocationDTO {
    return locationRepository.findAllByPrisonIdAndLocalName(prisonId, localName).firstOrNull { !it.isPermanentlyDeactivated() }?.toDto() ?: throw LocationNotFoundException("$prisonId-$localName")
  }
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
