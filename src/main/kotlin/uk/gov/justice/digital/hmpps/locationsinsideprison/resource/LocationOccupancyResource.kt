package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonerLocationService
import java.util.*

@RestController
@Validated
@RequestMapping("/location-occupancy", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Location Occupancy",
  description = "Returns location information with occupancy information",
)
class LocationOccupancyResource(
  private val locationService: LocationService,
  private val prisonerLocationService: PrisonerLocationService,
) {

  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @GetMapping("/cells-with-capacity/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "List of cells by group at prison which have capacity.",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns cells with capacity available",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCellsWithCapacity(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable prisonId: String,
    @Schema(description = "Location Id in the prison below which to find cells", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = false)
    @RequestParam(name = "locationId", required = false) locationId: UUID? = null,
    @Schema(description = "Group name for a sub location to find cells", example = "Wing A", required = false)
    @RequestParam(name = "groupName", required = false) groupName: String? = null,
    @Schema(description = "Only return cells of a specified specialist type", example = "CSU", required = false)
    @RequestParam(name = "specialistCellType", required = false) specialistCellType: SpecialistCellType? = null,
    @Schema(description = "Include prisoner details in this cell", required = false, defaultValue = "false")
    @RequestParam(name = "includePrisonerInformation", required = false, defaultValue = "false") includePrisonerInformation: Boolean = false,
  ): List<CellWithSpecialistCellTypes> = locationService.getCellsWithCapacity(
    prisonId = prisonId,
    locationId = locationId,
    groupName = groupName,
    specialistCellType = specialistCellType,
    includePrisonerInformation = includePrisonerInformation,
  )

  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @GetMapping("/reception/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Whether reception at a prison has space, and who is in reception.",
    description = """Capacity, occupancy and space are for the RECP location only. The list of prisoners covers everyone
      currently in the prison at RECP, COURT or TAP. CSWAP is not included. A prison without a RECP location
      reports zero capacity and no space, as does an inactive RECP. Requires role VIEW_LOCATIONS""",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns reception capacity, occupancy and the prisoners in reception",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getReceptionOccupancy(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable prisonId: String,
  ): ReceptionOccupancy = prisonerLocationService.receptionOccupancy(prisonId)
}

@Schema(description = "Reception capacity and occupancy, with the prisoners currently in reception")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReceptionOccupancy(
  @param:Schema(title = "RECP location identifier. Absent when the prison has no RECP location.", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0")
  val id: UUID? = null,
  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @param:Schema(description = "Path hierarchy of the reception location", example = "RECP", required = true)
  val pathHierarchy: String,
  @param:Schema(required = true, title = "Max capacity of RECP.", example = "40")
  val maxCapacity: Int,
  @param:Schema(required = true, title = "Working capacity of RECP. Zero means max capacity applies.", example = "0")
  val workingCapacity: Int,
  @param:Schema(required = true, title = "Whether RECP and all its parents are active. An inactive reception never has space.", example = "true")
  val active: Boolean,
  @param:Schema(required = true, title = "Number of prisoners currently in RECP.", example = "12")
  val noOfOccupants: Int,
  @param:Schema(title = "Prisoners currently in reception: those at RECP, COURT or TAP and in the prison", required = true)
  val prisoners: List<Prisoner>,
) {
  @Schema(description = "Business Key for the reception location", example = "MDI-RECP", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"

  /**
   * An inactive RECP reports a working capacity of 0 but keeps its max capacity, so the capacity rule alone would fall
   * back to max capacity and offer the inactive reception as a move destination.
   */
  @Schema(description = "True when RECP is active and has fewer occupants than its capacity (working capacity, or max capacity when working capacity is zero)", required = true)
  fun getHasSpace(): Boolean = active && noOfOccupants < (if (workingCapacity != 0) workingCapacity else maxCapacity)
}

@Schema(description = "Cell with specialist cell attributes details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellWithSpecialistCellTypes(
  @param:Schema(required = true, title = "Location identifier.", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0")
  val id: UUID,
  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @param:Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,
  @param:Schema(required = true, title = "Current occupancy of location.", example = "1")
  val noOfOccupants: Int,
  @param:Schema(required = true, title = "Max capacity of the location.", example = "2")
  val maxCapacity: Int,
  @param:Schema(required = true, title = "Working capacity of the location.", example = "1")
  val workingCapacity: Int,
  @param:Schema(title = "Local Name of the location.", example = "RES-HB1-ALE")
  val localName: String? = null,
  @param:Schema(title = "List of specialist types for the cell.", example = """[{ "typeCode": "LISTENER_CRISIS", "typeDescription": "Listener / crisis cell" }]""")
  val specialistCellTypes: List<CellType> = listOf(),
  @param:Schema(title = "List of the old location attributes.", example = """[{ "typeCode": "DOUBLE_OCCUPANCY", "typeDescription": "Double Occupancy" }]""")
  val legacyAttributes: List<ResidentialLocationAttribute> = listOf(),
  @param:Schema(title = "List prisoners in this cell", required = true)
  val prisonersInCell: List<Prisoner>? = null,
) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"

  @JsonIgnore
  fun hasSpace() = noOfOccupants < getActualCapacity()

  private fun getActualCapacity() = if (workingCapacity != 0) workingCapacity else maxCapacity

  @Schema(description = "Cell with specialist cell attribute")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class CellType(
    @param:Schema(title = "Specialist Cell Type Code", required = true)
    val typeCode: SpecialistCellType,
    @param:Schema(title = "Specialist Cell Type Description", required = true)
    val typeDescription: String,
  )

  @Schema(description = "Cell with old location attribute")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class ResidentialLocationAttribute(
    @param:Schema(title = "Attribute Type Code", required = true)
    val typeCode: ResidentialAttributeValue,
    @param:Schema(title = "Attribute Type Description", required = true)
    val typeDescription: String,
  )
}
