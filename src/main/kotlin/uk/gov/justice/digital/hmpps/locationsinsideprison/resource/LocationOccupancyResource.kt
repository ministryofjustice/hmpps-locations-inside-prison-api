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
  ): List<CellWithSpecialistCellTypes> =
    locationService.getCellsWithCapacity(
      prisonId = prisonId,
      locationId = locationId,
      groupName = groupName,
      specialistCellType = specialistCellType,
      includePrisonerInformation = includePrisonerInformation,
    )
}

@Schema(description = "Cell with specialist cell attributes details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellWithSpecialistCellTypes(
  @Schema(required = true, title = "Location identifier.", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0")
  val id: UUID,
  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,
  @Schema(required = true, title = "Current occupancy of location.", example = "1")
  val noOfOccupants: Int,
  @Schema(required = true, title = "Max capacity of the location.", example = "2")
  val maxCapacity: Int,
  @Schema(required = true, title = "Working capacity of the location.", example = "1")
  val workingCapacity: Int,
  @Schema(title = "Local Name of the location.", example = "RES-HB1-ALE")
  val localName: String? = null,
  @Schema(title = "List of specialist types for the cell.", example = """[{ "typeCode": "LISTENER_CRISIS", "typeDescription": "Listener / crisis cell" }]""")
  val specialistCellTypes: List<CellType> = listOf(),
  @Schema(title = "List of the old location attributes.", example = """[{ "typeCode": "DOUBLE_OCCUPANCY", "typeDescription": "Double Occupancy" }]""")
  val legacyAttributes: List<ResidentialLocationAttribute> = listOf(),
  @Schema(title = "List prisoners in this cell", required = true)
  val prisonersInCell: List<Prisoner>? = null,
) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }

  @JsonIgnore
  fun hasSpace() = noOfOccupants < getActualCapacity()

  private fun getActualCapacity() = if (workingCapacity != 0) workingCapacity else maxCapacity

  @Schema(description = "Cell with specialist cell attribute")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class CellType(
    @Schema(title = "Specialist Cell Type Code", required = true)
    val typeCode: SpecialistCellType,
    @Schema(title = "Specialist Cell Type Description", required = true)
    val typeDescription: String,
  )

  @Schema(description = "Cell with old location attribute")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class ResidentialLocationAttribute(
    @Schema(title = "Attribute Type Code", required = true)
    val typeCode: ResidentialAttributeValue,
    @Schema(title = "Attribute Type Description", required = true)
    val typeDescription: String,
  )
}
