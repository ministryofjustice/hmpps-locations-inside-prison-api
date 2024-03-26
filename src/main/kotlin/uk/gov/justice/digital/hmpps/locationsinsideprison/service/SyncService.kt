package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.MigrateHistoryRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpsertLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
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

  fun upsertLocation(upsert: UpsertLocationRequest): LocationDTO {
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

  fun migrate(upsert: UpsertLocationRequest): LocationDTO {
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

  private fun updateLocation(upsert: UpsertLocationRequest): LocationDTO {
    val locationToUpdate = locationRepository.findById(upsert.id!!)
      .orElseThrow { LocationNotFoundException(upsert.toString()) }

    if (upsert.deactivationReason != null && upsert.deactivatedDate == null) {
      throw ValidationException("When deactivating a location, the deactivated date must be provided")
    }

    findParent(upsert)?.let { parent ->
      if (parent.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      locationToUpdate.setParent(parent)
    }
    locationToUpdate.updateWith(upsert, upsert.lastUpdatedBy, clock)

    return locationToUpdate.toDto()
  }

  private fun createLocation(upsert: UpsertLocationRequest): LocationDTO {
    val locationToCreate = upsert.toNewEntity(clock)
    findParent(upsert)?.let { locationToCreate.setParent(it) }
    return locationRepository.save(locationToCreate).toDto()
  }

  private fun findParent(upsert: UpsertLocationRequest): Location? {
    return upsert.parentId?.let {
      locationRepository.findById(it).orElseThrow {
        LocationNotFoundException(it.toString())
      }
    } ?: upsert.parentLocationPath?.let {
      locationRepository.findOneByPrisonIdAndPathHierarchy(upsert.prisonId, upsert.parentLocationPath)
        ?: throw LocationNotFoundException(upsert.toString())
    }
  }

  fun migrateHistory(locationId: UUID, migrateHistoryRequest: MigrateHistoryRequest): ChangeHistory? {
    val location = locationRepository.findById(locationId)
      .orElseThrow { LocationNotFoundException(locationId.toString()) }

    with(migrateHistoryRequest) {
      return location.addHistory(
        attributeName = attribute,
        oldValue = oldValue,
        newValue = newValue,
        amendedBy = amendedBy,
        amendedDate = amendedDate,
      )?.toDto()
    }
  }
}
