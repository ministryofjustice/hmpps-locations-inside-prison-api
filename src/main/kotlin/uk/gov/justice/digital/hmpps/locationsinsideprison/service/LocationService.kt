package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class LocationService(
  private val locationRepository: LocationRepository,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationFacade: AuthenticationFacade,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getLocationById(id: UUID, includeChildren: Boolean = false): LocationDTO? {
    val toDto = locationRepository.findById(id).getOrNull()?.toDto(includeChildren)
    return toDto
  }

  fun getLocationByPrison(prisonId: String): List<LocationDTO> =
    locationRepository.findAllByPrisonIdOrderByPathHierarchy(prisonId).map {
      it.toDto()
    }

  fun getLocationByKey(key: String, includeChildren: Boolean = false): LocationDTO? {
    if (!key.contains("-")) throw LocationNotFoundException(key)

    val (prisonId, code) = key.split("-", limit = 2)
    return locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, code)?.toDto(includeChildren)
  }

  fun getLocations(pageable: Pageable = PageRequest.of(0, 20, Sort.by("id"))): Page<LocationDTO> {
    return locationRepository.findAll(pageable).map(Location::toDto)
  }

  @Transactional
  fun createResidentialLocation(createResidentialLocationRequest: CreateResidentialLocationRequest): LocationDTO {
    val parentLocation = createResidentialLocationRequest.parentId?.let {
      locationRepository.findById(createResidentialLocationRequest.parentId).getOrNull() ?: throw LocationNotFoundException(it.toString())
    }

    val pathHierarchy = if (parentLocation != null) {
      parentLocation.getPathHierarchy() + "-"
    } else {
      ""
    } + createResidentialLocationRequest.code
    locationRepository.findOneByPrisonIdAndPathHierarchy(createResidentialLocationRequest.prisonId, pathHierarchy)
      ?.let { throw LocationAlreadyExistsException("${createResidentialLocationRequest.prisonId}-$pathHierarchy") }

    val locationToCreate =
      createResidentialLocationRequest.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    parentLocation?.let { locationToCreate.setParent(it) }

    val location = locationRepository.save(locationToCreate).toDto()

    log.info("Created Residential Location [${location.id}]")
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
  fun createNonResidentialLocation(createNonResidentialLocationRequest: CreateNonResidentialLocationRequest): LocationDTO {
    val parentLocation = createNonResidentialLocationRequest.parentId?.let {
      locationRepository.findById(createNonResidentialLocationRequest.parentId).getOrNull() ?: throw LocationNotFoundException(it.toString())
    }

    val pathHierarchy = if (parentLocation != null) {
      parentLocation.getPathHierarchy() + "-"
    } else {
      ""
    } + createNonResidentialLocationRequest.code
    locationRepository.findOneByPrisonIdAndPathHierarchy(createNonResidentialLocationRequest.prisonId, pathHierarchy)
      ?.let { throw LocationAlreadyExistsException("${createNonResidentialLocationRequest.prisonId}-$pathHierarchy") }

    val locationToCreate = createNonResidentialLocationRequest.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)
    parentLocation?.let { locationToCreate.setParent(it) }
    val location = locationRepository.save(locationToCreate).toDto()

    log.info("Created Non Residential Location [${location.id}]")
    telemetryClient.trackEvent(
      "Created Non Residential Location",
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
  fun updateLocation(id: UUID, patchLocationRequest: PatchLocationRequest): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    val hierarchyChanged =
      patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getCode() ||
        patchLocationRequest.parentId != null && patchLocationRequest.parentId != locationToUpdate.getParent()?.id

    val cascadeUp = locationToUpdate is ResidentialLocation &&
      (
        (patchLocationRequest.certification != null && patchLocationRequest.certification != locationToUpdate.certification?.toDto()) ||
          (patchLocationRequest.capacity != null && patchLocationRequest.capacity != locationToUpdate.capacity?.toDto())
        )

    patchLocationRequest.parentId?.let {
      if (it == id) throw IllegalArgumentException("Cannot set parent to self")
      locationToUpdate.setParent(
        locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString()),
      )
    }

    if (hierarchyChanged) {
      // check that code doesn't clash with existing location
      val pathHierarchy = locationToUpdate.getParent()?.getPathHierarchy() + "-" + patchLocationRequest.code
      locationRepository.findOneByPrisonIdAndPathHierarchy(locationToUpdate.prisonId, pathHierarchy)
        ?.let { throw LocationAlreadyExistsException("${locationToUpdate.prisonId}-$pathHierarchy") }
    }

    locationToUpdate.updateWith(patchLocationRequest, authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Updated Location [$id]")
    telemetryClient.trackEvent(
      "Updated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto(includeChildren = hierarchyChanged, includeParent = hierarchyChanged || cascadeUp)
  }

  @Transactional
  fun deactivateLocation(id: UUID, deactivatedReason: DeactivatedReason): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationToUpdate.deactivate(deactivatedReason, authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Deactivated Location [$id]")
    telemetryClient.trackEvent(
      "Deactivated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto()
  }

  @Transactional
  fun reactivateLocation(id: UUID): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationToUpdate.reactivate(authenticationFacade.getUserOrSystemInContext(), clock)

    log.info("Re-activated Location [$id]")
    telemetryClient.trackEvent(
      "Re-activated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto()
  }

  @Transactional
  fun deleteLocation(id: UUID): LocationDTO {
    val locationToDelete = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationRepository.delete(locationToDelete)

    return locationToDelete.toDto()
  }
}
