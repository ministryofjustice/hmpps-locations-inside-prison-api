package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.ValidationException
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import java.util.UUID
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
) : EventBase() {

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
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "false") formatLocalName: Boolean = false,
  ) = locationService.getLocationById(id = id, includeChildren = includeChildren, includeHistory = includeHistory, formatLocalName = formatLocalName)
    ?: throw LocationNotFoundException(id.toString())

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
  ): Page<LegacyLocation> {
    if (pageable.pageSize > 200) {
      throw ValidationException("Page size must be 200 or less")
    }
    return locationService.getLocations(pageable)
  }

  @PutMapping("/{id}/change-local-name")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Update local name details of a location",
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
  fun updateLocalName(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    updateLocationLocalNameRequest: UpdateLocationLocalNameRequest,
  ): LocationDTO = eventPublishAndAudit(
    InternalLocationDomainEventType.LOCATION_AMENDED,
  ) {
    locationService.updateLocalName(
      id = id,
      updateLocationLocalNameRequest = updateLocationLocalNameRequest,
    )
  }

  @GetMapping("/{prisonId}/local-name/{localName}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Finds a location matching the local name for a given prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns cell location matching",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun findLocationByLocalName(
    @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable prisonId: String,
    @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
    @Size(max = 30, message = "Description must be less than 31 characters")
    @PathVariable localName: String,
    @Schema(description = "The level above in this hierarchy to look below, empty will check prison level", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = false)
    @RequestParam(name = "parentLocationId", required = false)
    parentLocationId: UUID? = null,
  ): LocationDTO = locationService.findByPrisonIdTopParentAndLocalName(prisonId = prisonId, parentLocationId = parentLocationId, localName = localName)

  @PutMapping("/{id}/deactivate/temporary")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Temporarily deactivate a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns deactivated locations",
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
  ): List<LocationDTO> = deactivate(locationService.deactivateLocations(DeactivateLocationsRequest(mapOf(id to temporaryDeactivationLocationRequest))))

  @PutMapping("/{id}/update/temporary-deactivation")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Update the details of the deactivation of a location",
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
  fun updateDeactivateDetails(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    updateDeactivationDetailsRequest: TemporaryDeactivationLocationRequest,
  ): LocationDTO = eventPublishAndAudit(
    InternalLocationDomainEventType.LOCATION_DEACTIVATED,
  ) {
    locationService.updateDeactivatedDetails(
      id,
      deactivatedReason = updateDeactivationDetailsRequest.deactivationReason,
      deactivationReasonDescription = updateDeactivationDetailsRequest.deactivationReasonDescription,
      proposedReactivationDate = updateDeactivationDetailsRequest.proposedReactivationDate,
      planetFmReference = updateDeactivationDetailsRequest.planetFmReference,
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
  ): LocationDTO = eventPublishAndAudit(
    InternalLocationDomainEventType.LOCATION_DEACTIVATED,
  ) {
    locationService.permanentlyDeactivateLocation(
      id,
      reasonForPermanentDeactivation = permanentDeactivationLocationRequest.reason,
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
        description = "Returns reactivation location",
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
  @Deprecated("Replaced by bulk update", replaceWith = ReplaceWith("PUT /locations/bulk/reactivate"))
  fun reactivateLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestParam(
      name = "cascade-reactivation",
      required = false,
      defaultValue = "false",
    ) cascadeReactivation: Boolean = false,
  ): LocationDTO = reactivate(locationService.reactivateLocations(ReactivateLocationsRequest(mapOf(id to ReactivationDetail(cascadeReactivation = cascadeReactivation))))).first()

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Delete a draft location",
    description = "Requires role ROLE_MAINTAIN_LOCATIONS and write scope, only locations marked as DRAFT can be deleted",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "Deleted location",
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
        description = "Missing required role. Requires the ROLE_MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteLocations(
    @Schema(description = "Location UUID to remove", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
    @PathVariable id: UUID,
  ) {
    eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_DELETED,
    ) {
      locationService.deleteLocation(id)
    }
  }
}
