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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.MigrateHistoryRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpsertLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SyncService
import java.util.*

@RestController
@Validated
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Migrate",
  description = "Migrate NOMIS to Locations Inside Prison Service API endpoints.",
)
@PreAuthorize("hasRole('ROLE_MIGRATE_LOCATIONS')")
class MigrationResource(
  private val syncService: SyncService,
) : EventBaseResource() {

  @PostMapping("/location")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Migrate a location",
    description = "Requires role MIGRATE_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Migrated location",
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
        description = "Missing required role. Requires the MIGRATE_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun migrateLocation(
    @RequestBody
    @Validated
    migrateRequest: UpsertLocationRequest,
  ) = syncService.migrate(migrateRequest)

  @PostMapping("/location/{id}/history")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Migrate a location history",
    description = "Requires role MIGRATE_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Migrated history",
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
        description = "Missing required role. Requires the MIGRATE_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun migrateHistory(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    locationHistory: MigrateHistoryRequest,
  ): ChangeHistory? = syncService.migrateHistory(id, locationHistory)
}
