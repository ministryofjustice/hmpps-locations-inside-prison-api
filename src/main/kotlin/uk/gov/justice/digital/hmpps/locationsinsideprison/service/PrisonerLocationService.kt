package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class PrisonerLocationService(
  private val cellLocationRepository: CellLocationRepository,
  private val locationRepository: LocationRepository,
  private val prisonerSearchService: PrisonerSearchService,
) {
  fun prisonersInPrison(prisonId: String): List<PrisonerLocation> {
    val locations = cellLocationRepository.findAllByPrisonIdAndActive(prisonId, true)
    return getPrisonersAndMap(prisonersInLocations(prisonId, locations.filter { !it.isPermanentlyDeactivated() }))
  }

  fun prisonersInPrisonAllLocations(prisonId: String): List<PrisonerLocation> {
    return getPrisonersAndMap(prisonerSearchService.getPrisonersInPrison(prisonId))
  }

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

  fun prisonersInLocations(location: Location): List<Prisoner> {
    return prisonersInLocations(location.prisonId, location.cellLocations())
  }

  fun prisonersInLocations(prisonId: String, locations: List<Cell>): List<Prisoner> {
    val locationsToCheck = locations.map { it.getPathHierarchy() }.sorted()
    return if (locationsToCheck.isNotEmpty()) {
      prisonerSearchService.findPrisonersInLocations(prisonId, locationsToCheck)
    } else {
      listOf()
    }
  }

  private fun getPrisonersAndMap(prisonerLocations: List<Prisoner>) =
    prisonerLocations
      .groupBy { it.cellLocation }
      .map {
        PrisonerLocation(
          cellLocation = it.key,
          prisoners = it.value,
        )
      }.sortedBy { it.cellLocation }
}

@Schema(description = "Prisoner Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonerLocation(
  @Schema(description = "Cell location of the prisoner", example = "1-1-001", required = true)
  val cellLocation: String,
  @Schema(description = "list of prisoners in the cell", required = true)
  val prisoners: List<Prisoner>,
)
