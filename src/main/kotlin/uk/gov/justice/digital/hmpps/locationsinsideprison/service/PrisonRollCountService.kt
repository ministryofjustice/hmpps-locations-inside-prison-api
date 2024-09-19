package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import java.util.*

@Service
@Transactional(readOnly = true)
class PrisonRollCountService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val prisonerLocationService: PrisonerLocationService,

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  fun getPrisonRollCount(prisonId: String): PrisonRollCount {
    val listOfPrisoners = prisonerLocationService.prisonersInPrisonAllLocations(prisonId)
      .flatMap { it.prisoners }

    val mapOfPrisoners = listOfPrisoners.filter { it.cellLocation != null }.groupBy { it.cellLocation!! }

    val locations: List<ResidentialPrisonerLocation> = residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)
      .filter { !it.isPermanentlyDeactivated() }
      .filter { it.isCell() || it.isLocationShownOnResidentialSummary() }
      .map { it.toResidentialPrisonerLocation(mapOfPrisoners) }
      .sortedWith(NaturalOrderComparator())

    val enRouteCount = 0
    val movementCount = MovementCount(0, 0)

    val currentRoll = listOfPrisoners.size
    val rollSummary = PrisonRollSummary(
      prisonId = prisonId,
      numUnlockRollToday = currentRoll - movementCount.numArrivedToday + movementCount.numOutToday,
      numCurrentPopulation = currentRoll,
      numArrivedToday = movementCount.numArrivedToday,
      numOutToday = movementCount.numOutToday,
      numOut = listOfPrisoners.filter { it.inOutStatus == "OUT" }.size,
      numTap = listOfPrisoners.filter { it.lastMovementTypeCode == "TAP" }.size,
      numCourt = listOfPrisoners.filter { it.lastMovementTypeCode == "COURT" }.size,
    )

    val prisonRollCount = PrisonRollCount(
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
      locations = locations.map {
        it.toDto()
      },
    )
    return prisonRollCount
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
  val locations: List<ResidentialLocationRollCount>,
)

data class ResidentialPrisonerLocation(
  val locationId: UUID,
  val key: String,
  val locationType: LocationType,
  val locationCode: String,
  val fullLocationPath: String,
  val localName: String? = null,
  val certified: Boolean = false,
  val status: LocationStatus,
  val deactivatedReason: DeactivatedReason? = null,
  val subLocations: List<ResidentialPrisonerLocation>,
  val capacity: Capacity? = null,
  val prisoners: List<Prisoner>? = null,
  val isAResidentialCell: Boolean = false,
) : SortAttribute {

  fun toDto() =
    ResidentialLocationRollCount(
      locationId = locationId,
      key = key,
      locationType = locationType,
      locationCode = locationCode,
      fullLocationPath = fullLocationPath,
      localName = localName,
      certified = certified,
      deactivatedReason = deactivatedReason,
      rollCount = getRollCount(),
      subLocations = removeLocations(subLocations),
    )

  private fun removeLocations(locations: List<ResidentialPrisonerLocation>): List<ResidentialLocationRollCount> {
    return locations
      .filter { it.status == LocationStatus.ACTIVE && !it.isAResidentialCell }
      .map {
        it.toDto()
      }
  }
  private fun getRollCount() =
    LocationRollCount(
      bedsInUse = getBedsInUse(),
      currentlyInCell = getCurrentlyInCell(),
      currentlyOut = getCurrentlyOut(),
      workingCapacity = capacity?.workingCapacity ?: 0,
      netVacancies = getNetVacancies(),
      outOfOrder = getOutOfOrder(),
    )

  fun getNetVacancies() = getActualCapacity() - getNumOfOccupants()

  private fun getNumOfOccupants(): Int = getCells().sumOf { it.prisoners?.size ?: 0 }

  private fun getActualCapacity() = capacity?.let { if (it.workingCapacity != 0) it.workingCapacity else it.maxCapacity } ?: 0

  fun getBedsInUse(): Int = getNumOfOccupants()

  fun getCurrentlyInCell(): Int = getCells().sumOf { it.prisoners?.filter { p -> p.inOutStatus == "IN" }?.size ?: 0 }

  fun getCurrentlyOut(): Int = getCells().sumOf { it.prisoners?.filter { p -> p.inOutStatus == "OUT" }?.size ?: 0 }

  fun getOutOfOrder(): Int = getCells().filter { it.status == LocationStatus.INACTIVE && it.deactivatedReason?.outOfUse == true }.size

  private fun getCells(): List<ResidentialPrisonerLocation> {
    val leafLocations = mutableListOf<ResidentialPrisonerLocation>()

    fun traverse(location: ResidentialPrisonerLocation) {
      if (location.isAResidentialCell) {
        leafLocations.add(location)
      } else {
        for (childLocation in location.subLocations) {
          traverse(childLocation)
        }
      }
    }

    traverse(this)
    return leafLocations
  }

  override fun getSortName() = localName?.capitalizeWords() ?: fullLocationPath
}

@Schema(description = "Residential Prisoner Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResidentialLocationRollCount(
  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Location Code", example = "001", required = true)
  val locationCode: String,

  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val fullLocationPath: String,

  @Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certified: Boolean = false,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @Schema(description = "Roll count details", required = true)
  val rollCount: LocationRollCount,

  @Schema(description = "Sub Locations", required = false)
  val subLocations: List<ResidentialLocationRollCount>? = null,

  @Schema(description = "Unique key to this location", example = "LEI-A-1-001", required = true)
  val key: String,
)

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
