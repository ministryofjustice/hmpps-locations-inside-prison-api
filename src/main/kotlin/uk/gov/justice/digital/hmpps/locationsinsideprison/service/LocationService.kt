package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationContainsPrisonersException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateLocationResult
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class LocationService(
  private val locationRepository: LocationRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val prisonerSearchService: PrisonerSearchService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationFacade: AuthenticationFacade,
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

  fun getLocationByKey(key: String, includeChildren: Boolean = false, includeHistory: Boolean = false): LocationDTO? {
    if (!key.contains("-")) throw LocationNotFoundException(key)

    val (prisonId, code) = key.split("-", limit = 2)
    return locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, code)?.toDto(includeChildren = includeChildren, includeHistory = includeHistory)
  }

  fun getLocations(pageable: Pageable = PageRequest.of(0, 20, Sort.by("id"))): Page<LocationDTO> {
    return locationRepository.findAll(pageable).map(Location::toDto)
  }

  @Transactional
  fun createLocation(request: CreateRequest): LocationDTO {
    val parentLocation = getParentLocation(request.parentId)

    checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with existing location

    val locationToCreate = request.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    parentLocation?.let { locationToCreate.setParent(it) }

    val capacityChanged = request is CreateResidentialLocationRequest && request.isCell() &&
      request.capacity != null

    val certificationChanged = request is CreateResidentialLocationRequest && request.isCell() &&
      request.certification != null

    val attributesChanged = request is CreateResidentialLocationRequest && request.isCell() &&
      request.attributes != null

    val usageChanged = request is CreateNonResidentialLocationRequest && request.usage != null

    val location = locationRepository.save(locationToCreate).toDto(includeParent = certificationChanged || capacityChanged || attributesChanged || usageChanged)

    log.info("Created Location [${location.id}] (Residential=${location.isResidential()})")
    telemetryClient.trackEvent(
      "Created Location (Residential=${location.isResidential()})",
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
    return locationRepository.save(wing).toDto(includeChildren = true)
  }

  @Transactional
  fun updateLocation(id: UUID, patchLocationRequest: PatchLocationRequest): UpdateLocationResult {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw ValidationException("Cannot update a permanently inactive location")
    }

    val codeChanged = patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getCode()
    val oldParent = locationToUpdate.getParent()
    val parentChanged = patchLocationRequest.parentId != null && patchLocationRequest.parentId != oldParent?.id

    if (parentChanged) locationToUpdate.addHistory(LocationAttribute.PARENT_LOCATION, oldParent?.id?.toString(), patchLocationRequest.parentId?.toString(), authenticationFacade.getUserOrSystemInContext(), LocalDateTime.now(clock))

    if (codeChanged || parentChanged) {
      val newCode = patchLocationRequest.code ?: locationToUpdate.getCode()
      val theParent = patchLocationRequest.parentId?.let {
        locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString())
      } ?: oldParent
      checkParentValid(theParent, newCode, locationToUpdate.prisonId)

      if (parentChanged && theParent?.id == id) throw ValidationException("Cannot set parent to self")
      theParent?.let { locationToUpdate.setParent(it) }
    }

    val capacityChanged = locationToUpdate is Cell &&
      patchLocationRequest.capacity != null && patchLocationRequest.capacity != locationToUpdate.getCapacity()

    val certificationChanged = locationToUpdate is Cell &&
      patchLocationRequest.certification != null && patchLocationRequest.certification != locationToUpdate.getCertification()

    val attributesChanged = locationToUpdate is Cell && patchLocationRequest.attributes != locationToUpdate.attributes.map { it.attributeValue }.toSet()

    locationToUpdate.updateWith(patchLocationRequest, authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Updated Location [$locationToUpdate]")
    telemetryClient.trackEvent(
      "Updated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
        "codeChanged" to "$codeChanged",
        "parentChanged" to "$parentChanged",
        "capacityChanged" to "$capacityChanged",
        "certificationChanged" to "$certificationChanged",
      ),
      null,
    )

    return UpdateLocationResult(
      locationToUpdate.toDto(includeChildren = codeChanged || parentChanged, includeParent = parentChanged || capacityChanged || certificationChanged || attributesChanged),
      capacityChanged,
      certificationChanged,
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  @Transactional
  fun deactivateLocation(
    id: UUID,
    deactivatedReason: DeactivatedReason? = null,
    proposedReactivationDate: LocalDate? = null,
    planetFmReference: String? = null,
    permanentDeactivation: Boolean = false,
  ): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val locationsToCheck = locationToUpdate.cellLocations().map { it.getPathHierarchy() }
    if (locationsToCheck.isNotEmpty()) {
      val locationsWithPrisoners =
        prisonerSearchService.findPrisonersInLocations(locationToUpdate.prisonId, locationsToCheck)

      if (locationsWithPrisoners.isNotEmpty()) {
        throw LocationContainsPrisonersException(locationsWithPrisoners)
      }
    }

    locationToUpdate.deactivate(
      deactivatedReason = deactivatedReason,
      deactivatedDate = LocalDate.now(clock),
      proposedReactivationDate = proposedReactivationDate,
      planetFmReference = planetFmReference,
      permanentDeactivation = permanentDeactivation,
      userOrSystemInContext = authenticationFacade.getUserOrSystemInContext(),
      clock = clock,
    )

    telemetryClient.trackEvent(
      "Deactivated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto(includeParent = true)
  }

  @Transactional
  fun reactivateLocation(id: UUID): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationToUpdate.reactivate(authenticationFacade.getUserOrSystemInContext(), clock)

    telemetryClient.trackEvent(
      "Re-activated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto(includeParent = true)
  }

  @Transactional
  fun convertToNonResidentialCell(id: UUID, convertedCellType: ConvertedCellType, otherConvertedCellType: String? = null): LocationDTO {
    val locationToConvert = residentialLocationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

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

  fun getLocationForPrisonBelowParent(
    prisonId: String,
    parentLocationId: UUID? = null,
    parentPathHierarchy: String? = null,
  ): List<LocationDTO> {
    val parentId =
      if (parentLocationId != null) {
        locationRepository.findById(parentLocationId).getOrNull()?.id
          ?: throw LocationNotFoundException(parentLocationId.toString())
      } else if (parentPathHierarchy != null) {
        locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, parentPathHierarchy)?.id
          ?: throw LocationNotFoundException("$prisonId-$parentPathHierarchy")
      } else {
        null
      }

    val locations =
      (
        if (parentId != null) {
          residentialLocationRepository.findAllByPrisonIdAndParentId(prisonId, parentId)
        } else {
          residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
        }
        )
        .filter { !it.isPermanentlyDeactivated() }
        .map { it.toDto(countInactiveCells = true) }

    return locations
  }

  fun getArchivedLocations(prisonId: String): List<LocationDTO> = residentialLocationRepository.findAllByPrisonIdAndArchivedIsTrue(prisonId).map {it.toDto()}

}
