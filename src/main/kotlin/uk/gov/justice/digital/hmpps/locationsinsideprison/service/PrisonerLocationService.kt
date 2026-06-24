package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class PrisonerLocationService(
  private val cellLocationRepository: CellLocationRepository,
  private val locationRepository: LocationRepository,
  private val prisonerSearchService: PrisonerSearchService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  fun prisonersInPrison(prisonId: String): List<PrisonerLocation> {
    val locations = cellLocationRepository.findAllByPrisonIdAndStatus(prisonId, LocationStatus.ACTIVE)
    return getPrisonersAndMap(prisonersInLocations(prisonId, locations.filter { !it.isPermanentlyDeactivated() }))
  }

  fun prisonersInPrisonAllLocations(prisonId: String): List<Prisoner> = prisonerSearchService.getPrisonersInPrison(prisonId)

  fun prisonersInLocations(key: String): List<PrisonerLocation> {
    val location = locationRepository.findOneByKey(key)
      ?: throw LocationNotFoundException("Location $key not found")

    return getPrisonersAndMap(prisonersInLocations(location.prisonId, location.cellLocations()))
  }

  fun prisonersInLocations(id: UUID): List<PrisonerLocation> {
    val location = locationRepository.findById(id).getOrNull()
      ?: throw LocationNotFoundException("Location $id not found")

    return getPrisonersAndMap(prisonersInLocations(location.prisonId, location.cellLocations()))
  }

  fun prisonersInLocations(location: Location): List<Prisoner> = prisonersInLocations(location.prisonId, location.cellLocations())

  fun prisonersInLocations(prisonId: String, locations: List<Cell>): List<Prisoner> {
    val locationsToCheck = locations.map { it.getPathHierarchy() }.sorted()
    return if (locationsToCheck.isNotEmpty()) {
      prisonerSearchService.findPrisonersInLocations(prisonId, locationsToCheck)
    } else {
      listOf()
    }
  }

  private fun getPrisonersAndMap(prisonerLocations: List<Prisoner>) = prisonerLocations
    .filter { it.cellLocation != null }
    .groupBy { it.cellLocation }
    .map {
      PrisonerLocation(
        cellLocation = it.key!!,
        prisoners = it.value,
      )
    }.sortedBy { it.cellLocation }
}

/**
 * Enforces that a capacity change does not drop a cell's certified capacity below the number of prisoners
 * currently held there. Max capacity is always checked. Working capacity is additionally checked for NORMAL
 * ACCOMMODATION cells only - segregation/healthcare/other cells may legitimately have a working capacity of 0
 * even while occupied, so only their max capacity is constrained.
 *
 * Deliberately a plain function (not a method on the @Transactional [PrisonerLocationService]) so that throwing
 * does not mark a participating transaction rollback-only: callers obtain [currentOccupancy] via the read-only
 * service and then call this, which lets the cell certificate upload mark an individual row as failed without
 * rolling back its per-row transaction, while the synchronous paths still roll back as intended.
 */
fun validateCapacityNotBelowOccupancy(location: Location, currentOccupancy: Int, newMaxCapacity: Int, newWorkingCapacity: Int) {
  if (newMaxCapacity < currentOccupancy) {
    throw CapacityException(
      location.getKey(),
      "Max capacity ($newMaxCapacity) cannot be decreased below current cell occupancy ($currentOccupancy)",
      ErrorCode.MaxCapacityCannotBeBelowOccupancyLevel,
    )
  }
  if (location is Cell && location.accommodationType == AccommodationType.NORMAL_ACCOMMODATION && newWorkingCapacity < currentOccupancy) {
    throw CapacityException(
      location.getKey(),
      "Working capacity ($newWorkingCapacity) cannot be decreased below current cell occupancy ($currentOccupancy)",
      ErrorCode.WorkingCapacityCannotBeBelowOccupancyLevel,
    )
  }
}

@Schema(description = "Prisoner Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonerLocation(
  @param:Schema(description = "Cell location of the prisoner", example = "1-1-001", required = true)
  val cellLocation: String,
  @param:Schema(description = "list of prisoners in the cell", required = true)
  val prisoners: List<Prisoner>,
)
