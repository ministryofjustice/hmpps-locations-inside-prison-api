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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Request to a create location and cell locations below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellInitialisationRequest(
  @Schema(
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

  @Schema(
    description = "Parent location under which the structure and/or cells should be created, if not specified then will add to the top level of the prison, is specified but no `newLevelAboveCells` is specified then cells will be created under this location",
    required = false,
  )
  val parentLocation: UUID? = null,

  @Schema(
    description = "The location to create above the cells, this is normally a landing or spur, if the location where cells should be created under already exists then leave null",
    required = false,
  )
  val newLevelAboveCells: LevelAboveCells? = null,

  @Schema(description = "Used For Types for all cells", required = false)
  val cellsUsedFor: Set<UsedForType>? = null,

  @Schema(
    description = "Accommodation Type for all cells",
    required = false,
    defaultValue = "NORMAL_ACCOMMODATION",
    example = "NORMAL_ACCOMMODATION",
  )
  val accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  val cells: Set<NewCellRequest>? = null,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createCells(
    createdBy: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
    location: ResidentialLocation,
  ) = cells?.map { cell ->
    Cell(
      prisonId = prisonId,
      code = cell.code,
      cellMark = cell.cellMark,
      pathHierarchy = "${location.getPathHierarchy()}-${cell.code}",
      status = LocationStatus.DRAFT,
      accommodationType = accommodationType,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = mutableListOf(),
      capacity = Capacity(
        maxCapacity = cell.maxCapacity,
        workingCapacity = cell.workingCapacity,
      ),
      certification = Certification(certifiedNormalAccommodation = cell.certifiedNormalAccommodation),
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
      location.addChildLocation(this)
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
}
data class LevelAboveCells(
  @Schema(description = "Code assigned to the new structural location", example = "1", required = true)
  @field:Size(max = 12, message = "Max of 12 characters")
  val levelCode: String,
  @Schema(description = "Alternative description to display for location", example = "Landing A", required = false)
  @field:Size(max = 80, message = "Max of 80 characters")
  val levelLocalName: String?,

  @Schema(description = "Parent location type", example = "LANDING", required = false, defaultValue = "LANDING")
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
    childLocations = mutableListOf(),
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

data class NewCellRequest(
  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be up to 12 characters")
  val code: String,

  @Schema(description = "Cell mark of the location", required = false, example = "A1", minLength = 1)
  @field:Size(min = 1, message = "Mark cannot be blank")
  @field:Size(max = 12, message = "Mark must be up to 12 characters")
  val cellMark: String? = null,

  @Schema(description = "CNA value", required = false, defaultValue = "0")
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  val certifiedNormalAccommodation: Int = 0,

  @Schema(description = "Max capacity of the location", example = "2", defaultValue = "0", required = false)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int = 0,

  @Schema(description = "Working capacity of the location", example = "2", defaultValue = "0", required = false)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int = 0,

  @Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: Set<SpecialistCellType>? = null,

  @Schema(description = "In-cell sanitation for cell", required = false, defaultValue = "true")
  val inCellSanitation: Boolean = true,
)

enum class ResidentialStructuralType(
  val locationType: LocationType,
) {
  WING(LocationType.WING),
  SPUR(LocationType.SPUR),
  LANDING(LocationType.LANDING),
  CELL(LocationType.CELL),
  ;

  fun getPlural() = "${locationType.description}s"
}
