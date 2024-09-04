package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.buildEventsToPublishOnUpdate
import java.util.*

@RestController
@Validated
@RequestMapping("/locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Locations",
  description = "Returns location information",
)
class LocationResidentialResource(
  private val locationService: LocationService,
) : EventBaseResource() {
  @GetMapping("/residential-summary/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return locations for this prison below the parent location, is not provided - top level (w.g. WINGS)",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns locations for this level",
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
  fun getLocationForPrisonBelowParent(
    @Schema(
      description = "Prison Id",
      example = "MDI",
      required = true,
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Parent location ID", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = false)
    @RequestParam(name = "parentLocationId", required = false)
    parentLocationId: UUID? = null,
    @Schema(
      description = "Parent location path hierarchy, can be a Wing code, or landing code",
      example = "A-1",
      required = false,
    )
    @RequestParam(name = "parentPathHierarchy", required = false)
    parentPathHierarchy: String? = null,
    @Schema(description = "Include latest history", required = false, defaultValue = "false")
    @RequestParam(name = "latestHistory", required = false, defaultValue = "false")
    latestHistory: Boolean = false,
  ): ResidentialSummary = locationService.getResidentialLocations(
    prisonId = prisonId,
    parentLocationId = parentLocationId,
    parentPathHierarchy = parentPathHierarchy,
    returnLatestHistory = latestHistory,
  )

  @PostMapping("/create-wing", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a residential wing with landings and cells",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created locations",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createWing(
    @RequestBody
    @Validated
    createWingRequest: CreateWingRequest,
  ): Location {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_CREATED,
    ) {
      locationService.createWing(createWingRequest)
    }
  }

  @PostMapping("/residential", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a residential location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created location",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createResidentialLocation(
    @RequestBody
    @Validated
    createResidentialLocationRequest: CreateResidentialLocationRequest,
  ): Location {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_CREATED,
    ) {
      locationService.createResidentialLocation(createResidentialLocationRequest)
    }
  }

  @PatchMapping("/residential/{id}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Partial update of a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun patchResidentialLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    patchLocationRequest: PatchResidentialLocationRequest,
  ): Location {
    val results = locationService.updateResidentialLocation(id, patchLocationRequest)
    eventPublish(buildEventsToPublishOnUpdate(results))
    audit(id.toString()) { results.location.copy(childLocations = null, parentLocation = null) }
    return results.location
  }

  @PutMapping("/{id}/convert-cell-to-non-res-cell")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Converts a cell to a non res cell location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun convertCellToNonResidentialLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    convertCellToNonResidentialLocationRequest: ConvertCellToNonResidentialLocationRequest,
  ): Location {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_AMENDED,
    ) {
      with(convertCellToNonResidentialLocationRequest) {
        locationService.convertToNonResidentialCell(id, convertedCellType, otherConvertedCellType)
      }
    }
  }

  @PutMapping("/{id}/convert-to-cell")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Makes a non-res cell location a cell",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location Accommodation Type Other Non Residential cannot be accepted",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun convertToCellLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    convertToCellRequest: ConvertToCellRequest,
  ): Location {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_AMENDED,
    ) {
      with(convertToCellRequest) {
        locationService.convertToCell(
          id = id,
          accommodationType = accommodationType,
          specialistCellTypes = specialistCellTypes,
          maxCapacity = maxCapacity,
          workingCapacity = workingCapacity,
          usedForTypes = usedForTypes,
        )
      }
    }
  }

  @Schema(description = "Request to convert a cell to a non-res location")
  data class ConvertCellToNonResidentialLocationRequest(
    @Schema(description = "Cell type to convert to", example = "SHOWER", required = true)
    val convertedCellType: ConvertedCellType,
    @Schema(description = "Other type of converted cell", example = "Swimming pool", required = false, maxLength = 255)
    @field:Size(max = 255, message = "Description of other cell type must be no more than 255 characters")
    val otherConvertedCellType: String? = null,
  )

  @Schema(description = "Request to convert a non-res location to a cell")
  data class ConvertToCellRequest(
    @Schema(description = "Accommodation type of the location", example = "NORMAL_ACCOMMODATION", required = true)
    val accommodationType: AllowedAccommodationTypeForConversion,
    @Schema(description = "Specialist cell types", example = "[ \"BIOHAZARD_DIRTY_PROTEST\", \"ACCESSIBLE_CELL\" ]", required = false)
    val specialistCellTypes: Set<SpecialistCellType>? = null,
    @Schema(description = "Max capacity", example = "2", required = true)
    @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
    @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
    val maxCapacity: Int = 0,
    @Schema(description = "Working capacity", example = "1", required = true)
    @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
    @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
    val workingCapacity: Int = 0,
    @Schema(description = "Used For list", example = "[ \"STANDARD_ACCOMMODATION\", \"PERSONALITY_DISORDER\" ]", required = false)
    val usedForTypes: List<UsedForType>? = null,
  )

  @Schema(description = "Allowable Accommodation Types")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  enum class AllowedAccommodationTypeForConversion(
    val mapsTo: AccommodationType,
  ) {
    NORMAL_ACCOMMODATION(AccommodationType.NORMAL_ACCOMMODATION),
    HEALTHCARE_INPATIENTS(AccommodationType.HEALTHCARE_INPATIENTS),
    CARE_AND_SEPARATION(AccommodationType.CARE_AND_SEPARATION),
  }
}
