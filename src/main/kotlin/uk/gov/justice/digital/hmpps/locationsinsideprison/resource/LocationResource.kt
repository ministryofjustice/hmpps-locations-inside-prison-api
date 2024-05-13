package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.ValidationException
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.PositiveOrZero
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InformationSource
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialSummary
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO
@RestController
@Validated
@RequestMapping("/locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Locations",
  description = "Returns location information",
)
class LocationResource(
  private val locationService: LocationService,
) : EventBaseResource() {

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Returns location information for this ID",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location",
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
  fun getLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestParam(name = "includeChildren", required = false, defaultValue = "false") includeChildren: Boolean = false,
    @RequestParam(name = "includeHistory", required = false, defaultValue = "false") includeHistory: Boolean = false,
  ) =
    locationService.getLocationById(id = id, includeChildren = includeChildren, includeHistory = includeHistory) ?: throw LocationNotFoundException(id.toString())

  @GetMapping("/key/{key}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Returns location information for this key",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location",
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
  fun getLocationByKey(
    @Schema(description = "Location Key", example = "MDI-A-1-001", required = true)
    @PathVariable
    key: String,
    @RequestParam(name = "includeChildren", required = false, defaultValue = "false") includeChildren: Boolean = false,
    @RequestParam(name = "includeHistory", required = false, defaultValue = "false") includeHistory: Boolean = false,
  ) =
    locationService.getLocationByKey(key = key, includeChildren = includeChildren, includeHistory = includeHistory) ?: throw LocationNotFoundException(key)

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
    @PathVariable
    prisonId: String,
  ): List<LocationDTO> = locationService.getLocationByPrison(prisonId)

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
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @PathVariable
    prisonId: String,
    @Schema(description = "Parent location ID", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = false)
    @RequestParam(name = "parentLocationId", required = false)
    parentLocationId: UUID? = null,
    @Schema(description = "Parent location path hierarchy, can be a Wing code, or landing code", example = "A-1", required = false)
    @RequestParam(name = "parentPathHierarchy", required = false)
    parentPathHierarchy: String? = null,
  ): ResidentialSummary = locationService.getResidentialLocations(prisonId, parentLocationId, parentPathHierarchy)

  @GetMapping("")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get locations, filtered and paged",
    description = "Requires VIEW_LOCATIONS role, max of 200 records per request",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "A page of locations are returned",
      ),
      ApiResponse(
        responseCode = "400",
        description = "When input parameters are not valid",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the VIEW_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @Suppress("ktlint:standard:function-signature")
  fun getLocations(
    @ParameterObject
    @PageableDefault(page = 0, size = 20, sort = ["id"], direction = Sort.Direction.ASC)
    pageable: Pageable,
  ): Page<LocationDTO> {
    if (pageable.pageSize > 200) {
      throw ValidationException("Page size must be 200 or less")
    }
    return locationService.getLocations(pageable)
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
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_CREATED,
      {
        locationService.createLocation(createResidentialLocationRequest)
      },
      InformationSource.DPS,
    )
  }

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
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_CREATED,
      {
        locationService.createWing(createWingRequest)
      },
      InformationSource.DPS,
    )
  }

  @PostMapping("/non-residential", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a non-residential location",
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
  fun createNonResidentialLocation(
    @RequestBody
    @Validated
    createNonResidentialLocationRequest: CreateNonResidentialLocationRequest,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_CREATED,
      {
        locationService.createLocation(createNonResidentialLocationRequest)
      },
      InformationSource.DPS,
    )
  }

  @PatchMapping("/{id}")
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
  fun patchLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    patchLocationRequest: PatchLocationRequest,
  ): LocationDTO {
    val results = locationService.updateLocation(id, patchLocationRequest)
    eventPublish(buildEvents(results))
    audit(AuditType.LOCATION_AMENDED, id.toString()) { results.location.copy(childLocations = null, parentLocation = null) }
    return results.location
  }

  fun buildEvents(results: UpdateLocationResult): () -> Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val locationsChanged = if (results.otherParentLocationChanged != null) {
      listOf(results.location, results.otherParentLocationChanged)
    } else {
      listOf(results.location)
    }

    return {
      mapOf(
        InternalLocationDomainEventType.LOCATION_AMENDED to locationsChanged,
      )
    }
  }

  @PutMapping("/{id}/capacity")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Set the capacity of a cell",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns cell location changes",
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
  fun changeCapacity(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    capacity: Capacity,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_AMENDED,
      {
        locationService.updateCellCapacity(
          id = id,
          maxCapacity = capacity.maxCapacity,
          workingCapacity = capacity.workingCapacity,
        )
      },
      InformationSource.DPS,
    )
  }

  @PutMapping("/{id}/deactivate/temporary")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Temporarily deactivate a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns deactivated location",
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
  fun temporarilyDeactivateLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    temporaryDeactivationLocationRequest: TemporaryDeactivationLocationRequest,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_DEACTIVATED,
      {
        locationService.deactivateLocation(
          id,
          deactivatedReason = temporaryDeactivationLocationRequest.deactivationReason,
          proposedReactivationDate = temporaryDeactivationLocationRequest.proposedReactivationDate,
          planetFmReference = temporaryDeactivationLocationRequest.planetFmReference,
        )
      },
      InformationSource.DPS,
    )
  }

  @PutMapping("/{id}/deactivate/permanent")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Permanently deactivate a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns archived location",
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
  fun permanentlyDeactivateLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    permanentDeactivationLocationRequest: PermanentDeactivationLocationRequest,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_DEACTIVATED,
      {
        locationService.permanentlyDeactivateLocation(
          id,
          reasonForPermanentDeactivation = permanentDeactivationLocationRequest.reason,
        )
      },
      InformationSource.DPS,
    )
  }

  @PutMapping("/{id}/reactivate")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Re-activate a location",
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
  fun reactivateLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_REACTIVATED,
      {
        locationService.reactivateLocation(id)
      },
      InformationSource.DPS,
    )
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
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_AMENDED,
      {
        with(convertCellToNonResidentialLocationRequest) {
          locationService.convertToNonResidentialCell(id, convertedCellType, otherConvertedCellType)
        }
      },
      InformationSource.DPS,
    )
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
    ],
  )
  fun convertCellToNonResidentialLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    convertToCellRequest: ConvertToCellRequest,
  ): LocationDTO {
    return eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_AMENDED,
      {
        with(convertToCellRequest) {
          locationService.convertToCell(
            id = id,
            accommodationType = accommodationType,
            specialistCellType = specialistCellType,
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            usedForTypes = usedForTypes,
          )
        }
      },
      InformationSource.DPS,
    )
  }
}

@Schema(description = "Request to convert a cell to a non-res location")
data class ConvertCellToNonResidentialLocationRequest(
  @Schema(description = "Cell type to convert to", example = "SHOWER", required = true)
  val convertedCellType: ConvertedCellType,
  @Schema(description = "Other type of converted cell", example = "Swimming pool", required = false)
  val otherConvertedCellType: String? = null,
)

@Schema(description = "Request to convert a non-res location to a cell")
data class ConvertToCellRequest(
  @Schema(description = "Accommodation type of the location", example = "NORMAL_ACCOMMODATION", required = true)
  val accommodationType: AccommodationType,
  @Schema(description = "Specialist cell type", example = "BIOHAZARD_DIRTY_PROTEST", required = false)
  val specialistCellType: SpecialistCellType?,
  @Schema(description = "Max capacity", example = "2", required = true)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int = 0,
  @Schema(description = "Working capacity", example = "1", required = true)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int = 0,
  @Schema(description = "Used For list", example = "STANDARD_ACCOMMODATION, PERSONALITY_DISORDER", required = false)
  val usedForTypes: List<UsedForType>? = null,
)

data class UpdateLocationResult(
  val location: LocationDTO,
  val otherParentLocationChanged: LocationDTO? = null,
)
