package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
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

  fun getLocationById(id: UUID): LocationDTO? {
    return locationRepository.findById(id).getOrNull()?.toDto()
  }

  fun getLocationByKey(key: String): LocationDTO? {
    val (prisonId, path) = key.split("-", limit = 2)
    return locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, path)?.toDto()
  }

  @Transactional
  fun createLocation(createLocationRequest: CreateLocationRequest): LocationDTO {
    val locationToCreate = createLocationRequest.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)

    createLocationRequest.parentId?.let {
      locationToCreate.setParent(locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString()))
    }

    val location = locationRepository.save(locationToCreate).toDto()

    log.info("Created Location [${location.id}]")
    telemetryClient.trackEvent(
      "Created Location",
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
  fun updateLocation(id: UUID, updateLocationRequest: UpdateLocationRequest): LocationDTO {
    val locationToUpdate = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }

    updateLocationRequest.parentId?.let {
      locationToUpdate.setParent(locationRepository.findById(it).getOrNull() ?: throw LocationNotFoundException(it.toString()))
    }

    locationToUpdate.setCode(updateLocationRequest.code)
    locationToUpdate.locationType = updateLocationRequest.locationType
    locationToUpdate.updatedBy = authenticationFacade.getUserOrSystemInContext()
    locationToUpdate.whenUpdated = LocalDateTime.now(clock)

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
