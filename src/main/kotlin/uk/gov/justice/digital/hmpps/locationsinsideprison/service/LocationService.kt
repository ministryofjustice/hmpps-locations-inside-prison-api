package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
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

  fun getLocationById(id: UUID): LocationDTO? {
    return locationRepository.findById(id).getOrNull()?.toDto()
  }

  fun getLocationByKey(key: String): LocationDTO? {
    val (prisonId, code) = key.split("-", limit = 2)
    return locationRepository.findOneByPrisonIdAndCode(prisonId, code)?.toDto()
  }

  @Transactional
  fun createLocation(createLocationRequest: CreateLocationRequest): LocationDTO {
    val locationToCreate = createLocationRequest.toNewEntity(authenticationFacade.getUserOrSystemInContext(), clock)

    (
      if (createLocationRequest.parentId != null) {
        locationRepository.findById(createLocationRequest.parentId).getOrNull() ?: throw LocationNotFoundException(
          createLocationRequest.parentId.toString(),
        )
      } else {
        null
      }
      )?.addChildLocation(locationToCreate)

    val location = locationRepository.save(locationToCreate).toDto()

    log.info("Created Location [${location.id}]")
    telemetryClient.trackEvent(
      "Created Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "code" to location.code,
      ),
      null,
    )

    return location
  }
}
