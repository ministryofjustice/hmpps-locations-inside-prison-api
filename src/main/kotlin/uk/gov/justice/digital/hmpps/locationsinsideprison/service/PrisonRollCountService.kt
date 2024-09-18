package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
open class PrisonRollCountService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val prisonerLocationService: PrisonerLocationService,

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  fun getPrisonRollCount(prisonId: String, locationId: UUID? = null): PrisonRollCount {
    val topLocation =
      if (locationId != null) {
        residentialLocationRepository.findById(locationId).getOrNull()
          ?: throw LocationNotFoundException(locationId.toString())
      } else {
        null
      }

    val topLocationId = topLocation?.id
    val listOfPrisoners = if (topLocationId != null) {
      prisonerLocationService.prisonersInLocations(topLocationId)
    } else {
      prisonerLocationService.prisonersInPrisonAllLocations(prisonId)
    }.flatMap { it.prisoners }

    val mapOfPrisoners = listOfPrisoners.groupBy { it.cellLocation }

    val locations: List<ResidentialPrisonerLocation> = (
      if (topLocationId != null) {
        residentialLocationRepository.findAllByPrisonIdAndParentId(prisonId, topLocationId)
      } else {
        residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
      }
      )
      .filter { !it.isPermanentlyDeactivated() }
      .filter { it.isCell() || it.isLocationShownOnResidentialSummary() }
      .map { it.toResidentialPrisonerLocation(mapOfPrisoners) }
      .sortedWith(NaturalOrderComparator())

    val enRouteCount = 0
    val movementCount = MovementCount(0, 0)

    val rollSummary = PrisonRollSummary(
      prisonId = prisonId,
      numUnlockRollToday = locations.sumOf { it.getCurrentRoll() } - movementCount.numArrivedToday + movementCount.numOutToday,
      numCurrentPopulation = locations.sumOf { it.getCurrentRoll() },
      numArrivedToday = movementCount.numArrivedToday,
      numOutToday = movementCount.numOutToday,
      numOut = listOfPrisoners.filter { it.inOutStatus == "OUT" }.size,
      numTap = listOfPrisoners.filter { it.lastMovementTypeCode == "TAP" }.size,
      numCourt = listOfPrisoners.filter { it.lastMovementTypeCode == "COURT" }.size,
    )

    return PrisonRollCount(
      prisonId = prisonId,
      numUnlockRollToday = rollSummary.numUnlockRollToday,
      numCurrentPopulation = rollSummary.numCurrentPopulation,
      numOutToday = rollSummary.numOutToday,
      numArrivedToday = rollSummary.numArrivedToday,
      numInReception = mapOfPrisoners["RECP"]?.size ?: 0,
      numStillToArrive = enRouteCount,
      numNoCellAllocated = mapOfPrisoners["CSWAP"]?.size ?: 0,
      totals = LocationRollCount(
        bedsInUse = locations.sumOf { it.getBedsInUse() },
        currentlyInCell = locations.sumOf { it.getCurrentlyInCell() },
        currentlyOut = locations.sumOf { it.getCurrentlyOut() },
        workingCapacity = locations.sumOf { it.capacity?.workingCapacity ?: 0 },
        netVacancies = locations.sumOf { it.getNetVacancies() },
        outOfOrder = locations.sumOf { it.getOutOfOrder() },
      ),
      locations = locations,
    )
  }
}

data class MovementCount(
  val numArrivedToday: Int,
  val numOutToday: Int,
)

@Schema(description = "Prison Roll Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonRollSummary(
  @Schema(description = "Prison Id", required = true)
  val prisonId: String,
  @Schema(description = "Unlock roll today", required = true)
  val numUnlockRollToday: Int,
  @Schema(description = "Arrived today", required = true)
  val numArrivedToday: Int,
  @Schema(description = "Out today", required = true)
  val numOutToday: Int,
  @Schema(description = "Number out of prison", required = true)
  val numOut: Int,
  @Schema(description = "Number out on temporary absence", required = true)
  val numTap: Int,
  @Schema(description = "Number out at court", required = true)
  val numCourt: Int,
  @Schema(description = "Current population", required = true)
  val numCurrentPopulation: Int,
)

@Schema(description = "Establishment Roll Count")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonRollCount(
  @Schema(description = "Prison Id", required = true)
  val prisonId: String,
  @Schema(description = "Unlock roll today", required = true)
  val numUnlockRollToday: Int,
  @Schema(description = "Current population", required = true)
  val numCurrentPopulation: Int,
  @Schema(description = "Arrived today", required = true)
  val numArrivedToday: Int,
  @Schema(description = "In reception", required = true)
  val numInReception: Int,
  @Schema(description = "Still to arrive", required = true)
  val numStillToArrive: Int,
  @Schema(description = "Out today", required = true)
  val numOutToday: Int,
  @Schema(description = "No cell allocated", required = true)
  val numNoCellAllocated: Int,

  @Schema(description = "Totals", required = true)
  val totals: LocationRollCount,

  @Schema(description = "Residential location roll count summary", required = true)
  val locations: List<ResidentialPrisonerLocation>,
)

@Schema(description = "Residential Prisoner Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResidentialPrisonerLocation(
  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Location Code", example = "001", required = true)
  val code: String,

  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @Schema(description = "Capacity details of the location", required = false)
  val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certification: Certification? = null,

  @Schema(description = "Current Level within hierarchy, starts at 1, e.g Wing = 1", examples = ["1", "2", "3"], required = true)
  val level: Int,

  @Schema(description = "Indicates this is the lowest level, often a cell", example = "false", required = true)
  val leafLevel: Boolean,

  @Schema(description = "Parent Location", required = false)
  val parentLocation: ResidentialPrisonerLocation? = null,

  @Schema(description = "Sub Locations", required = false)
  val childLocations: List<ResidentialPrisonerLocation>? = null,

  @Schema(description = "list of prisoners in the cell", required = true)
  val prisoners: List<Prisoner>,

  @Schema(description = "Status of the location", example = "ACTIVE", required = true)
  val status: LocationStatus,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @Schema(description = "Indicated the location is a cell", example = "true", required = true)
  val cell: Boolean = false,

) : SortAttribute {

  @Schema(description = "Beds in use", required = true)
  fun getCurrentRoll(): Int = getCells().sumOf { it.prisoners.size }

  @Schema(description = "Beds in use", required = true)
  fun getBedsInUse(): Int = getCells().sumOf { it.prisoners.size }

  @Schema(description = "Currently in cell", required = true)
  fun getCurrentlyInCell(): Int = getCells().sumOf { it.prisoners.filter { p -> p.inOutStatus == "IN" }.size }

  @Schema(description = "Currently out", required = true)
  fun getCurrentlyOut(): Int = getCells().sumOf { it.prisoners.filter { p -> p.inOutStatus == "OUT" }.size }

  @Schema(description = "Net vacancies", required = true)
  fun getNetVacancies(): Int = 0

  @Schema(description = "Out of order", required = true)
  fun getOutOfOrder(): Int = getCells().filter { it.status == LocationStatus.INACTIVE && it.deactivatedReason?.outOfUse == true }.size

  fun getCells(): List<ResidentialPrisonerLocation> {
    val leafLocations = mutableListOf<ResidentialPrisonerLocation>()

    fun traverse(location: ResidentialPrisonerLocation) {
      if (location.cell) {
        leafLocations.add(location)
      } else {
        if (location.childLocations != null) {
          for (childLocation in location.childLocations) {
            traverse(childLocation)
          }
        }
      }
    }

    traverse(this)
    return leafLocations
  }

  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }

  @JsonIgnore
  override fun getSortName(): String {
    return localName?.capitalizeWords() ?: pathHierarchy
  }
}

@Schema(description = "Summary of cell usage for this level")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationRollCount(
  @Schema(description = "Beds in use", required = true)
  val bedsInUse: Int = 0,
  @Schema(description = "Currently in cell", required = true)
  val currentlyInCell: Int = 0,
  @Schema(description = "Currently out", required = true)
  val currentlyOut: Int = 0,
  @Schema(description = "Working capacity", required = true)
  val workingCapacity: Int = 0,
  @Schema(description = "Net vacancies", required = true)
  val netVacancies: Int = 0,
  @Schema(description = "Out of order", required = true)
  val outOfOrder: Int = 0,
)
