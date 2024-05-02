package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisMigrationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import java.time.Clock
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional
class SyncService(
  private val locationRepository: LocationRepository,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun sync(upsert: NomisSyncLocationRequest): LocationDTO {
    val location = if (upsert.id != null) {
      updateLocation(upsert)
    } else {
      createLocation(upsert)
    }

    log.info("Synchronised Location: ${location.id} (created: ${upsert.id == null}, updated: ${upsert.id != null})")
    telemetryClient.trackEvent(
      "Synchronised Location",
      mapOf(
        "created" to "${upsert.id == null}",
        "updated" to "${upsert.id != null}",
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )
    return location
  }

  fun migrate(upsert: NomisMigrationRequest): LocationDTO {
    val location = createLocation(upsert)

    log.info("Migrated Location: ${location.id}")
    telemetryClient.trackEvent(
      "Migrated Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )
    return location
  }

  private fun updateLocation(upsert: NomisSyncLocationRequest): LocationDTO {
    val locationToUpdate = locationRepository.findById(upsert.id!!)
      .orElseThrow { LocationNotFoundException(upsert.toString()) }

    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException("Location ${locationToUpdate.getKey()} cannot be updated as permanently deactivated")
    }
    findParent(upsert)?.let { parent ->
      if (parent.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      locationToUpdate.setParent(parent)
    }
    locationToUpdate.updateWith(upsert, upsert.lastUpdatedBy, clock)

    return locationToUpdate.toDto()
  }

  private fun createLocation(upsert: NomisMigrationRequest): LocationDTO {
    val locationToCreate = upsert.toNewEntity(clock)
    findParent(upsert)?.let { locationToCreate.setParent(it) }
    return locationRepository.save(locationToCreate).toDto()
  }

  private fun findParent(upsert: NomisMigrationRequest): Location? {
    return upsert.parentId?.let {
      locationRepository.findById(it).orElseThrow {
        LocationNotFoundException(it.toString())
      }
    } ?: upsert.parentLocationPath?.let {
      locationRepository.findOneByPrisonIdAndPathHierarchy(upsert.prisonId, upsert.parentLocationPath!!)
        ?: throw LocationNotFoundException(upsert.toString())
    }
  }

  fun deleteLocation(id: UUID): LocationDTO {
    val deletedLocation = locationRepository.findById(id)
      .orElseThrow { LocationNotFoundException(id.toString()) }.toDto()

    locationRepository.deleteById(id)
    log.info("Deleted Location: $id (${deletedLocation.getKey()})")
    telemetryClient.trackEvent(
      "Deleted Location",
      mapOf(
        "id" to id.toString(),
        "prisonId" to deletedLocation.prisonId,
        "key" to deletedLocation.getKey(),
      ),
      null,
    )
    return deletedLocation
  }
}
