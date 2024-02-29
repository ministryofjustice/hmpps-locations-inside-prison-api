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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
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
    locationRepository.findAllByPrisonIdOrderByPathHierarchy(prisonId).map {
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
    val location = locationRepository.save(locationToCreate).toDto()

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
  fun updateLocation(id: UUID, patchLocationRequest: PatchLocationRequest): UpdateLocationResult {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

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
      patchLocationRequest.capacity != null && patchLocationRequest.capacity != locationToUpdate.capacity?.toDto()

    val certificationChanged = locationToUpdate is Cell &&
      patchLocationRequest.certification != null && patchLocationRequest.certification != locationToUpdate.certification?.toDto()

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
      locationToUpdate.toDto(includeChildren = codeChanged || parentChanged, includeParent = parentChanged || capacityChanged || certificationChanged),
      capacityChanged,
      certificationChanged,
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  @Transactional
  fun deactivateLocation(id: UUID, deactivatedReason: DeactivatedReason, proposedReactivationDate: LocalDate? = null): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationToUpdate.deactivate(deactivatedReason, proposedReactivationDate, authenticationFacade.getUserOrSystemInContext(), clock)

    telemetryClient.trackEvent(
      "Deactivated Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to locationToUpdate.prisonId,
        "path" to locationToUpdate.getPathHierarchy(),
      ),
      null,
    )

    return locationToUpdate.toDto(includeChildren = true)
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

    return locationToUpdate.toDto(includeChildren = true)
  }

  @Transactional
  fun deleteLocation(id: UUID): LocationDTO {
    val locationToDelete = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    locationRepository.delete(locationToDelete)

    return locationToDelete.toDto()
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
}
