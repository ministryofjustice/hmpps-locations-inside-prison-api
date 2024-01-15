package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.InternalLocationDomainEventType
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
  ): LocationDTO {
    return auditWrapper(AuditType.LOCATION_RETRIEVED, id.toString()) {
      locationService.getLocationById(id, includeChildren) ?: throw LocationNotFoundException(id.toString())
    }
  }

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
  ): LocationDTO {
    return auditWrapper(AuditType.LOCATION_RETRIEVED, key) {
      locationService.getLocationByKey(key, includeChildren) ?: throw LocationNotFoundException(key)
    }
  }

  @PostMapping("", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a location",
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
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createLocation(
    @RequestBody
    @Validated
    createLocationRequest: CreateLocationRequest,
  ): LocationDTO {
    return eventPublishAndAuditWrapper(InternalLocationDomainEventType.LOCATION_CREATED) {
      locationService.createLocation(createLocationRequest)
    }
  }

  @PutMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Updates a location",
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
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    updateLocationRequest: UpdateLocationRequest,
  ): LocationDTO {
    return eventPublishAndAuditWrapper(InternalLocationDomainEventType.LOCATION_AMENDED) {
      locationService.updateLocation(id, updateLocationRequest)
    }
  }

  @DeleteMapping("/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Deletes a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Returns deleted location",
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
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
  ) {
    eventPublishAndAuditWrapper(InternalLocationDomainEventType.LOCATION_DELETED) {
      locationService.deleteLocation(id)
    }
  }
}
