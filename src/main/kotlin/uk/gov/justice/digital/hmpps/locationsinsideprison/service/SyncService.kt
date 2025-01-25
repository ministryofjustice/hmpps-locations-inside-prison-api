package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityManager
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class SyncService(
  private val locationRepository: LocationRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val entityManager: EntityManager,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getLegacyLocation(id: UUID, includeHistory: Boolean = false): LegacyLocation? {
    return locationRepository.findById(id).getOrNull()?.toLegacyDto(includeHistory = includeHistory)
  }

  fun sync(upsert: NomisSyncLocationRequest): LegacyLocation {
    val location = if (upsert.id != null) {
      updateLocation(upsert.id, upsert)
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

  private fun updateLocation(id: UUID, upsert: NomisSyncLocationRequest): LegacyLocation {
    var locationToUpdate = locationRepository.findById(id).getOrNull()
      ?: throw LocationNotFoundException(id.toString())

    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException("Location ${locationToUpdate.getKey()} cannot be updated as permanently deactivated")
    }

    if (locationToUpdate is ResidentialLocation && locationToUpdate.isConvertedCell()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException("Location ${locationToUpdate.getKey()} cannot be updated as has been converted to non-res cell")
    }

    val linkedTransaction = createLinkedTransaction(prisonId = upsert.prisonId, TransactionType.SYNC, "NOMIS Sync (Update) [${locationToUpdate.getPathHierarchy()}] in prison ${locationToUpdate.prisonId}", upsert.lastUpdatedBy)

    locationToUpdate = handleChangeOfType(id, locationToUpdate, upsert, linkedTransaction)

    findParent(upsert)?.let { parent ->
      if (parent.id == id) throw ValidationException("Cannot set parent to self")
      locationToUpdate.setParent(parent)
    }
    locationToUpdate.sync(upsert, clock, linkedTransaction)

    return locationToUpdate.toLegacyDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  private fun handleChangeOfType(
    id: UUID,
    location: Location,
    upsert: NomisSyncLocationRequest,
    linkedTransaction: LinkedTransaction,
  ): Location {
    var clearSessionRequired = false
    if (upsert.residentialHousingType == null) {
      if (location is ResidentialLocation) {
        if (location is Cell) {
          location.convertToNonCell()
        }
        locationRepository.updateResidentialHousingTypeToNull(id)
        clearSessionRequired = true
      }
    } else {
      if (location is NonResidentialLocation) {
        location.updateUsage(emptySet(), upsert.lastUpdatedBy, clock, linkedTransaction)
        locationRepository.updateResidentialHousingType(id, upsert.residentialHousingType.name, upsert.residentialHousingType.mapToAccommodationType().name)
        clearSessionRequired = true
      }
    }

    if (upsert.locationType != location.locationType) {
      if (location is Cell) {
        location.convertToNonCell()
      }
      locationRepository.updateLocationType(id, upsert.locationType.name)
      if (location is ResidentialLocation && upsert.locationType == LocationType.CELL) {
        locationRepository.updateResidentialHousingType(
          id,
          location.residentialHousingType.name,
          location.residentialHousingType.mapToAccommodationType().name,
        )
      }
      clearSessionRequired = true
    }

    if (clearSessionRequired) {
      entityManager.flush()
      entityManager.clear()
    }

    return locationRepository.findById(id).getOrNull() ?: throw LocationNotFoundException(id.toString())
  }

  private fun createLocation(upsert: NomisSyncLocationRequest): LegacyLocation {
    val linkedTransaction = createLinkedTransaction(prisonId = upsert.prisonId, TransactionType.SYNC, "<pending>", upsert.lastUpdatedBy)

    val locationToCreate = upsert.toNewEntity(clock, linkedTransaction)
    findParent(upsert)?.let { locationToCreate.setParent(it) }
    return locationRepository.save(locationToCreate).toLegacyDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
      linkedTransaction.transactionDetail = "NOMIS Sync (Create) [${it.pathHierarchy}] in prison ${it.prisonId}"
    }
  }

  private fun findParent(upsert: NomisSyncLocationRequest): Location? {
    return upsert.parentId?.let {
      locationRepository.findById(it).orElseThrow {
        LocationNotFoundException(it.toString())
      }
    } ?: upsert.parentLocationPath?.let {
      locationRepository.findOneByPrisonIdAndPathHierarchy(upsert.prisonId, upsert.parentLocationPath)
        ?: throw LocationNotFoundException(upsert.toString())
    }
  }

  fun deleteLocation(id: UUID): LegacyLocation {
    return locationRepository.findById(id).getOrNull()?.also {
      if (it.findSubLocations().isNotEmpty()) {
        throw ValidationException("Cannot delete location with sub-locations")
      }

      val tx = createLinkedTransaction(prisonId = it.prisonId, TransactionType.DELETE, "NOMIS Sync (Delete) [${it.getPathHierarchy()}] in prison ${it.prisonId}", "NOMIS")

      locationRepository.deleteLocationById(id)
      log.info("Deleted Location: $id (${it.getKey()})")
      telemetryClient.trackEvent(
        "Deleted Location",
        mapOf(
          "id" to id.toString(),
          "prisonId" to it.prisonId,
          "key" to it.getKey(),
        ),
        null,
      )
      tx.txEndTime = LocalDateTime.now(clock)
    }?.toLegacyDto()
      ?: throw LocationNotFoundException(id.toString())
  }

  private fun createLinkedTransaction(prisonId: String, type: TransactionType, detail: String, transactionInvokedBy: String): LinkedTransaction {
    val linkedTransaction = LinkedTransaction(
      prisonId = prisonId,
      transactionType = type,
      transactionDetail = detail,
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = LocalDateTime.now(clock),
    )
    return linkedTransactionRepository.save(linkedTransaction).also {
      LocationService.log.info("Created linked transaction: $it")
    }
  }
}
