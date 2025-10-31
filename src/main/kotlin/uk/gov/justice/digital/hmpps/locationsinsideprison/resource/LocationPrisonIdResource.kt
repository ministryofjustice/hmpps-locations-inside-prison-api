package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationPrefixDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PrisonHierarchyDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialService
import java.util.UUID

@RestController
@Validated
@RequestMapping("/locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Locations",
  description = "Returns location information",
)
class LocationPrisonIdResource(
  private val locationService: LocationService,
  private val nonResidentialService: NonResidentialService,
) : EventBase() {

  @GetMapping("/prison/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return locations for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns locations",
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
  fun getLocationForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
  ): List<Location> = locationService.getLocationByPrison(prisonId)

  @GetMapping("/prison/{prisonId}/groups")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return location groups for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location groups",
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
  fun getLocationGroupsForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
  ): List<LocationGroupDto> = locationService.getLocationGroupsForPrison(prisonId)

  @GetMapping("/prison/{prisonId}/residential-hierarchy")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return the entire hierarchy for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns hierarchy for this prison",
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
  fun getPrisonHierarchy(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "The maximum level to return, default is all if not defined.  Most APIs will just need the first level so maxLevel=1", example = "1", required = false)
    @RequestParam(name = "maxLevel", required = false)
    maxLevel: Int? = null,
    @Schema(description = "Include virtual locations such as CSWAP and RECP", example = "false", required = false)
    @RequestParam(name = "includeVirtualLocations", required = false, defaultValue = "false")
    includeVirtualLocations: Boolean = false,
    @Schema(description = "Include temporarily inactive locations", example = "false", required = false)
    @RequestParam(name = "includeInactive", required = false, defaultValue = "false")
    includeInactive: Boolean = false,
  ): List<PrisonHierarchyDto> = locationService.getPrisonResidentialHierarchy(prisonId = prisonId, includeVirtualLocations = includeVirtualLocations, maxLevel = maxLevel, includeInactive = includeInactive)

  @GetMapping("/prison/{prisonId}/residential-hierarchy/{parentPathHierarchy}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return the hierarchy below the parent path specified for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns hierarchy for this prison below the path specified for this prison",
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
  fun getPrisonHierarchyBelowLocation(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Parent path", example = "A-1", required = true)
    @Size(min = 1, message = "Path must be a minimum of 1 characters")
    @NotBlank(message = "Path cannot be blank")
    @PathVariable
    parentPathHierarchy: String,
    @Schema(description = "The maximum level to return, default is all if not defined.  Most APIs will just need the first level so maxLevel=1", example = "1", required = false)
    @RequestParam(name = "maxLevel", required = false)
    maxLevel: Int? = null,
    @Schema(description = "Include virtual locations such as CSWAP and RECP", example = "false", required = false)
    @RequestParam(name = "includeVirtualLocations", required = false, defaultValue = "false")
    includeVirtualLocations: Boolean = false,
    @Schema(description = "Include temporarily inactive locations", example = "false", required = false)
    @RequestParam(name = "includeInactive", required = false, defaultValue = "false")
    includeInactive: Boolean = false,
  ): List<PrisonHierarchyDto> = locationService.getPrisonResidentialHierarchy(prisonId = prisonId, includeVirtualLocations = includeVirtualLocations, maxLevel = maxLevel, includeInactive = includeInactive, parentPathHierarchy = parentPathHierarchy)

  @GetMapping("/prison/{prisonId}/residential-first-level")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return the entire hierarchy for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns hierarchy for this prison",
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
  fun getTopLevelResidentialLocations(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Include virtual locations such as CSWAP and RECP", example = "false", required = false)
    @RequestParam(name = "includeVirtualLocations", required = false, defaultValue = "false")
    includeVirtualLocations: Boolean = false,
  ): List<PrisonHierarchyDto> = locationService.getPrisonResidentialHierarchy(prisonId = prisonId, includeVirtualLocations = includeVirtualLocations, maxLevel = 1)

  @GetMapping("/prison/{prisonId}/group/{group}/location-prefix")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Get location prefix by group.",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
      ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getLocationPrefixFromGroup(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable("prisonId")
    prisonId: String,
    @Schema(description = "The group name", example = "Houseblock 1", required = true)
    @PathVariable("group")
    group: String,
  ): LocationPrefixDto = locationService.getLocationPrefixFromGroup(prisonId, group)

  @GetMapping("/groups/{prisonId}/{name}")
  @Operation(
    summary = "List of cell locations by group at prison location.",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns cell locations by group",
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
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  fun getLocationGroup(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Parameter(description = "Group name", required = true)
    @PathVariable("name")
    name: String,
  ) = locationService.getCellLocationsForGroup(prisonId, name)

  @GetMapping("/prison/{prisonId}/archived")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return residential archived locations for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns archived locations",
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
  fun getArchivedLocationForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
  ) = locationService.getArchivedLocations(prisonId)

  @GetMapping("/prison/{prisonId}/inactive-cells")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return residential inactive cells for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns inactive locations",
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
  fun getInactiveLocationsForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "location ID below which all inactive cells will be returned", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = false)
    @RequestParam(name = "parentLocationId", required = false)
    parentLocationId: UUID? = null,
  ) = locationService.getResidentialInactiveLocations(prisonId, parentLocationId)

  @GetMapping("/prison/{prisonId}/location-type/{locationType}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return locations by their type for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns locations",
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
  fun getLocationsByPrisonAndLocationType(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Location type", example = "CELL", required = true)
    @PathVariable
    locationType: LocationType,
    @RequestParam(name = "sortByLocalName", required = false, defaultValue = "false") sortByLocalName: Boolean = false,
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "false") formatLocalName: Boolean = false,
  ): List<Location> = locationService.getLocationByPrisonAndLocationType(prisonId, locationType, sortByLocalName, formatLocalName)
}
