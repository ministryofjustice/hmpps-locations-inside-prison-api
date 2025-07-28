package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.getCSwapLocationCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.getReceptionLocationCodes
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class PrisonRollCountService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val prisonerLocationService: PrisonerLocationService,
  private val prisonApiService: PrisonApiService,
  private val prisonConfigurationRepository: PrisonConfigurationRepository,

) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonRollCount(prisonId: String, includeCells: Boolean = false): PrisonRollCount {
    val movements = prisonApiService.getMovementTodayInAndOutOfPrison(prisonId)

    val listOfPrisoners = prisonerLocationService.prisonersInPrisonAllLocations(prisonId)
    val mapOfPrisoners = listOfPrisoners.filter { it.cellLocation != null }.groupBy { it.cellLocation!! }

    return prisonRollCount(
      prisonId,
      mapOfPrisoners,
      listOfPrisoners,
      movements,
      includeCells,
      residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId),
      segShouldBeFiltered(prisonId),
    )
  }

  fun getPrisonCellRollCount(prisonId: String, locationId: UUID): PrisonCellRollCount {
    val currentLocation = residentialLocationRepository.findById(locationId).getOrNull() ?: throw LocationNotFoundException(locationId.toString())
    if (currentLocation.isPermanentlyDeactivated()) {
      throw LocationNotFoundException("${currentLocation.getKey()} : location not found or permanently deactivated")
    }
    if (!currentLocation.isLocationShownOnResidentialSummary()) {
      throw LocationNotFoundException("${currentLocation.getKey()} : location cannot be displayed as not a residential location")
    }

    val listOfPrisoners = prisonerLocationService.prisonersInLocations(prisonId, currentLocation.cellLocations())
    val mapOfPrisoners = listOfPrisoners.filter { it.cellLocation != null }.groupBy { it.cellLocation!! }

    val locations = currentLocation.toResidentialPrisonerLocation(mapOfPrisoners)

    val filterSeg = segShouldBeFiltered(prisonId)
    val prisonRollCount = PrisonCellRollCount(
      locationHierarchy = currentLocation.getHierarchy(),
      totals = locationRollCount(listOf(locations), filterSeg),
      locations = removeLocations(listOf(locations), includeCells = true, filterSeg = filterSeg),
    )
    return prisonRollCount
  }

  private fun segShouldBeFiltered(prisonId: String) = !(prisonConfigurationRepository.findById(prisonId).getOrNull()?.includeSegregationInRollCount ?: false)

  private fun prisonRollCount(
    prisonId: String,
    mapOfPrisoners: Map<String, List<Prisoner>>,
    listOfPrisoners: List<Prisoner>,
    movements: PrisonRollMovementInfo,
    includeCells: Boolean,
    residentialLocations: List<ResidentialLocation>,
    filterSeg: Boolean,
  ): PrisonRollCount {
    val locations: List<ResidentialPrisonerLocation> =
      residentialLocations
        .filter { !it.isPermanentlyDeactivated() }
        .filter { it.isLocationShownOnResidentialSummary() }
        .map { it.toResidentialPrisonerLocation(mapOfPrisoners) }
        .sortedWith(NaturalOrderComparator())

    val currentRoll = listOfPrisoners.count { it.inOutStatus == "IN" }
    val numInReception = listOfPrisoners.count { it.cellLocation in getReceptionLocationCodes() && it.inOutStatus == "IN" }
    val numNoCellAllocated = listOfPrisoners.count { it.cellLocation == getCSwapLocationCode() && it.inOutStatus == "IN" }

    val prisonRollCount = PrisonRollCount(
      prisonId = prisonId,
      numUnlockRollToday = currentRoll - movements.inOutMovementsToday.`in` + movements.inOutMovementsToday.out,
      numCurrentPopulation = currentRoll,
      numOutToday = movements.inOutMovementsToday.out,
      numArrivedToday = movements.inOutMovementsToday.`in`,
      numInReception = numInReception,
      numStillToArrive = movements.enRouteToday,
      numNoCellAllocated = numNoCellAllocated,
      totals = locationRollCount(locations, filterSeg),
      locations = removeLocations(locations, includeCells = includeCells, filterSeg = filterSeg),
    )
    return prisonRollCount
  }

  private fun locationRollCount(locations: List<ResidentialPrisonerLocation>, filterSeg: Boolean) = LocationRollCount(
    bedsInUse = locations.sumOf { it.getBedsInUse() },
    currentlyInCell = locations.sumOf { it.getCurrentlyInCell() },
    currentlyOut = locations.sumOf { it.getCurrentlyOut() },
    workingCapacity = locations.sumOf { it.getWorkingCapacity() },
    netVacancies = locations.sumOf {
      it.getWorkingCapacity() - it.getCurrentlyInCell(filterSeg) - it.getCurrentlyOut(
        filterSeg,
      )
    },
    outOfOrder = locations.sumOf { it.getOutOfOrder() },
  )
}

data class ResidentialPrisonerLocation(
  val locationId: UUID,
  val key: String,
  val locationType: LocationType,
  val locationCode: String,
  val fullLocationPath: String,
  val localName: String? = null,
  val certified: Boolean = false,
  val status: DerivedLocationStatus,
  val deactivatedReason: DeactivatedReason? = null,
  val subLocations: List<ResidentialPrisonerLocation>,
  val capacity: Capacity? = null,
  val prisoners: List<Prisoner>? = null,
  val isLeafLevel: Boolean = false,
  val accommodationType: AccommodationType? = null,
) : SortAttribute {

  fun toDto(includeCells: Boolean = false, filterSeg: Boolean) = ResidentialLocationRollCount(
    locationId = locationId,
    key = key,
    locationType = locationType,
    locationCode = locationCode,
    fullLocationPath = fullLocationPath,
    localName = localName,
    certified = certified,
    deactivatedReason = deactivatedReason,
    rollCount = getRollCount(filterSeg),
    subLocations = removeLocations(subLocations, includeCells = includeCells, filterSeg = filterSeg),
  )

  private fun getRollCount(filterSeg: Boolean) = LocationRollCount(
    bedsInUse = getBedsInUse(),
    currentlyInCell = getCurrentlyInCell(),
    currentlyOut = getCurrentlyOut(),
    workingCapacity = getWorkingCapacity(),
    netVacancies = getNetVacancies(filterSeg),
    outOfOrder = getOutOfOrder(),
  )

  fun getWorkingCapacity() = capacity?.workingCapacity ?: 0

  fun getBedsInUse(): Int = getNumOfOccupants()

  fun getCurrentlyInCell(filterSeg: Boolean = false): Int = getCells(filterSeg)
    .sumOf { it.prisoners?.filter { p -> p.inOutStatus == "IN" }?.size ?: 0 }

  fun getCurrentlyOut(filterSeg: Boolean = false): Int = getCells(filterSeg).sumOf { it.prisoners?.filter { p -> p.inOutStatus == "OUT" }?.size ?: 0 }

  fun getOutOfOrder(): Int = getCells().filter { it.status == DerivedLocationStatus.INACTIVE }.size

  private fun getNetVacancies(filterSeg: Boolean) = getWorkingCapacity() - getNumOfOccupants(filterSeg)

  private fun getNumOfOccupants(filterSeg: Boolean = false): Int = getCells(filterSeg).sumOf { it.prisoners?.size ?: 0 }

  private fun getCells(filterCareAndSeparation: Boolean) = if (filterCareAndSeparation) getNonCareAndSeparationCells() else getCells()

  private fun getNonCareAndSeparationCells() = getCells().filter { it.accommodationType != AccommodationType.CARE_AND_SEPARATION }

  private fun getCells(): List<ResidentialPrisonerLocation> {
    val leafLocations = mutableListOf<ResidentialPrisonerLocation>()

    fun traverse(location: ResidentialPrisonerLocation) {
      if (location.isLeafLevel) {
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

@Schema(description = "Establishment Roll Count")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonRollCount(
  @param:Schema(description = "Prison Id", required = true)
  val prisonId: String,
  @param:Schema(description = "Unlock roll today", required = true)
  val numUnlockRollToday: Int,
  @param:Schema(description = "Current population", required = true)
  val numCurrentPopulation: Int,
  @param:Schema(description = "Arrived today", required = true)
  val numArrivedToday: Int,
  @param:Schema(description = "In reception", required = true)
  val numInReception: Int,
  @param:Schema(description = "Still to arrive", required = true)
  val numStillToArrive: Int,
  @param:Schema(description = "Out today", required = true)
  val numOutToday: Int,
  @param:Schema(description = "No cell allocated", required = true)
  val numNoCellAllocated: Int,

  @param:Schema(description = "Totals", required = true)
  val totals: LocationRollCount,

  @param:Schema(description = "Residential location roll count summary", required = true)
  val locations: List<ResidentialLocationRollCount>,
)

@Schema(description = "Establishment Roll Count for Cells")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonCellRollCount(
  @param:Schema(description = "Parent locations, top to bottom", required = true)
  val locationHierarchy: List<LocationSummary>? = null,

  @param:Schema(description = "Totals", required = true)
  val totals: LocationRollCount,

  @param:Schema(description = "Residential location roll count summary", required = true)
  val locations: List<ResidentialLocationRollCount>,
)

@Schema(description = "Summary of cell usage for this level")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationRollCount(
  @param:Schema(description = "Beds in use", required = true)
  val bedsInUse: Int = 0,
  @param:Schema(description = "Currently in cell", required = true)
  val currentlyInCell: Int = 0,
  @param:Schema(description = "Currently out", required = true)
  val currentlyOut: Int = 0,
  @param:Schema(description = "Working capacity", required = true)
  val workingCapacity: Int = 0,
  @param:Schema(description = "Net vacancies", required = true)
  val netVacancies: Int = 0,
  @param:Schema(description = "Out of order", required = true)
  val outOfOrder: Int = 0,
)

@Schema(description = "Residential Prisoner Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResidentialLocationRollCount(
  @param:Schema(description = "Unique key to this location", example = "LEI-A-1-001", required = true)
  val key: String,

  @param:Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,

  @param:Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @param:Schema(description = "Location Code", example = "001", required = true)
  val locationCode: String,

  @param:Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val fullLocationPath: String,

  @param:Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @param:Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certified: Boolean = false,

  @param:Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @param:Schema(description = "Roll count details", required = true)
  val rollCount: LocationRollCount,

  @param:Schema(description = "Sub Locations", required = false)
  val subLocations: List<ResidentialLocationRollCount>? = null,

)

fun removeLocations(locations: List<ResidentialPrisonerLocation>, includeCells: Boolean = false, filterSeg: Boolean): List<ResidentialLocationRollCount> = locations
  .filter { it.status in listOf(DerivedLocationStatus.ACTIVE) && (includeCells || !it.isLeafLevel) }
  .map {
    it.toDto(includeCells, filterSeg)
  }
