package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInitialisationRequest.Companion.log
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.validateCapacity
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Request to a create location and cell locations below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellInitialisationRequest(
  @param:Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @param:Schema(
    description = "Parent location under which the structure and/or cells should be created, if not specified then will add to the top level of the prison, is specified but no `newLevelAboveCells` is specified then cells will be created under this location",
    required = false,
  )
  val parentLocation: UUID? = null,

  @param:Schema(
    description = "The location to create above the cells, this is normally a landing or spur, if the location where cells should be created under already exists then leave null",
    required = false,
  )
  val newLevelAboveCells: LevelAboveCells? = null,

  @param:Schema(description = "Used For Types for all cells", required = false)
  val cellsUsedFor: Set<UsedForType>? = null,

  @param:Schema(
    description = "Accommodation Type for all cells",
    required = false,
    defaultValue = "NORMAL_ACCOMMODATION",
    example = "NORMAL_ACCOMMODATION",
  )
  val accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @param:Schema(
    description = "Set of cells that are to be created",
    required = false,
  )
  val cells: Set<CellInformation>? = null,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createCells(
    createdBy: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
    parentLocation: ResidentialLocation,
  ) = cells?.map { cell ->

    addCellToParent(
      prisonId = prisonId,
      accommodationType = accommodationType,
      cellsUsedFor = cellsUsedFor,
      cell = cell,
      parentLocation = parentLocation,
      createdBy = createdBy,
      clock = clock,
      linkedTransaction = linkedTransaction,
    )
  }
}

fun addCellToParent(
  prisonId: String,
  accommodationType: AccommodationType,
  cellsUsedFor: Set<UsedForType>?,
  cell: CellInformation,
  parentLocation: ResidentialLocation,
  createdBy: String,
  clock: Clock,
  linkedTransaction: LinkedTransaction,
): Cell {
  val pathHierarchy = "${parentLocation.getPathHierarchy()}-${cell.code}"
  val key = "$prisonId-$pathHierarchy"

  validateCapacity(
    locationKey = key,
    certifiedNormalAccommodation = cell.certifiedNormalAccommodation,
    workingCapacity = cell.workingCapacity,
    maxCapacity = cell.maxCapacity,
    accommodationType = accommodationType,
    specialistCellTypes = cell.specialistCellTypes ?: emptySet(),
  )
  return Cell(
    prisonId = prisonId,
    code = cell.code,
    cellMark = cell.cellMark,
    pathHierarchy = pathHierarchy,
    status = LocationStatus.DRAFT,
    accommodationType = accommodationType,
    createdBy = createdBy,
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
    capacity = Capacity(
      maxCapacity = cell.maxCapacity,
      workingCapacity = cell.workingCapacity,
      certifiedNormalAccommodation = cell.certifiedNormalAccommodation,
    ),
    inCellSanitation = cell.inCellSanitation,
  ).apply {
    cell.specialistCellTypes?.forEach {
      addSpecialistCellType(
        specialistCellType = it,
        userOrSystemInContext = createdBy,
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
    }
    cellsUsedFor?.forEach {
      addUsedFor(
        usedForType = it,
        userOrSystemInContext = createdBy,
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
    }
    parentLocation.addChildLocation(this)
    addHistory(
      attributeName = LocationAttribute.LOCATION_CREATED,
      oldValue = null,
      newValue = getKey(),
      amendedBy = createdBy,
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = linkedTransaction,
    )
    log.info("Created cell ${this.getKey()}")
  }
}

@Schema(description = "Holds information about the level above which the cells should be created")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LevelAboveCells(
  @param:Schema(description = "Code assigned to the new structural location", example = "1", required = true)
  @field:Size(max = 12, message = "Max of 12 characters")
  val levelCode: String,
  @param:Schema(description = "Alternative description to display for location", example = "Landing A", required = false)
  @field:Size(max = 80, message = "Max of 80 characters")
  val levelLocalName: String?,

  @param:Schema(description = "Parent location type", example = "LANDING", required = false, defaultValue = "LANDING")
  val locationType: ResidentialStructuralType = ResidentialStructuralType.LANDING,
) {
  fun createLocation(prisonId: String, createdBy: String, clock: Clock, linkedTransaction: LinkedTransaction, parentLocation: ResidentialLocation? = null) = ResidentialLocation(
    prisonId = prisonId,
    code = levelCode,
    locationType = LocationType.valueOf(locationType.name),
    status = LocationStatus.DRAFT,
    pathHierarchy = levelCode,
    localName = levelLocalName,
    createdBy = createdBy,
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
  ).apply {
    parentLocation?.let { setParent(it) }
    addHistory(
      attributeName = LocationAttribute.LOCATION_CREATED,
      oldValue = null,
      newValue = getKey(),
      amendedBy = createdBy,
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = linkedTransaction,
    )
    log.info("Created location ${this.getKey()}")
  }
}

@Schema(description = "Information about cells to be created or updated")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellInformation(

  @param:Schema(description = "ID of the location, update only", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = false)
  val id: UUID? = null,

  @param:Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be up to 12 characters")
  val code: String,

  @param:Schema(description = "Cell mark of the location", required = false, example = "A1", minLength = 1)
  @field:Size(min = 1, message = "Mark cannot be blank")
  @field:Size(max = 12, message = "Mark must be up to 12 characters")
  val cellMark: String? = null,

  @param:Schema(description = "CNA value", required = false, defaultValue = "0")
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  val certifiedNormalAccommodation: Int = 0,

  @param:Schema(description = "Max capacity of the location", example = "2", defaultValue = "0", required = false)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int = 0,

  @param:Schema(description = "Working capacity of the location", example = "2", defaultValue = "0", required = false)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int = 0,

  @param:Schema(description = "Specialist cell types", required = false)
  val specialistCellTypes: Set<SpecialistCellType>? = null,

  @param:Schema(description = "In-cell sanitation for cell", required = false, defaultValue = "true")
  val inCellSanitation: Boolean = true,
)
enum class ResidentialStructuralType(
  val locationType: LocationType,
  val defaultNextLevel: ResidentialStructuralType? = null,
) {
  CELL(LocationType.CELL),
  LANDING(LocationType.LANDING, CELL),
  SPUR(LocationType.SPUR, LANDING),
  WING(LocationType.WING, LANDING),
  ;

  fun getPlural() = "${locationType.description}s"
}
