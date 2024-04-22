package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InformationSource
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SyncService
import java.util.*

@RestController
@Validated
@RequestMapping("/sync", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Sync",
  description = "Sync NOMIS to Locations Inside Prison Service API endpoints.",
)
@PreAuthorize("hasRole('ROLE_SYNC_LOCATIONS') and hasAuthority('SCOPE_write')")
class SyncResource(
  private val syncService: SyncService,
) : EventBaseResource() {

  @PostMapping("/upsert")
  @Operation(
    summary = "Upsert of a location",
    description = "Requires role SYNC_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Updated location",
      ),
      ApiResponse(
        responseCode = "201",
        description = "Created location",
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
        description = "Missing required role. Requires the SYNC_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun upsertLocation(
    @RequestBody
    @Validated
    syncRequest: NomisSyncLocationRequest,
  ): ResponseEntity<Location> {
    val eventType = if (syncRequest.id != null) {
      InternalLocationDomainEventType.LOCATION_AMENDED
    } else {
      InternalLocationDomainEventType.LOCATION_CREATED
    }
    return ResponseEntity(
      eventPublishAndAudit(
        eventType,
        function = {
          syncService.sync(syncRequest)
        },
        informationSource = InformationSource.NOMIS,
      ),
      if (syncRequest.id != null) {
        HttpStatus.OK
      } else {
        HttpStatus.CREATED
      },
    )
  }

  @DeleteMapping("/delete/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Delete a location",
    description = "Requires role SYNC_LOCATIONS and write scope",
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
        description = "Missing required role. Requires the SYNC_LOCATIONS role with write scope.",
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
    @Schema(description = "Location UUID, provided if already exists", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
    @PathVariable id: UUID,
  ) {
    eventPublishAndAudit(
      InternalLocationDomainEventType.LOCATION_DELETED,
      { syncService.deleteLocation(id) },
      informationSource = InformationSource.NOMIS,
    )
  }
}
