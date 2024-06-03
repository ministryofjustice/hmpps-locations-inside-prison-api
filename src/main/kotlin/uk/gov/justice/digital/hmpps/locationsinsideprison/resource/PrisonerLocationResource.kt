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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonerLocationService
import java.util.*

@RestController
@Validated
@RequestMapping("/prisoner-locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Prisoner locations",
  description = "Returns the locations prisoners",
)
class PrisonerLocationResource(
  private val prisonerLocationService: PrisonerLocationService,
) {

  @GetMapping("/id/{id}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('VIEW_PRISONER_LOCATIONS')")
  @Operation(
    summary = "Returns a map of prisoners in cells within this location",
    description = "Requires role VIEW_PRISONER_LOCATIONS",
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
        description = "Missing required role. Requires the VIEW_PRISONER_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonersInLocationById(
    @Schema(description = "Location Id, can be a wing, landing or cell", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
  ) = prisonerLocationService.prisonersInLocations(id)

  @GetMapping("/key/{key}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('VIEW_PRISONER_LOCATIONS')")
  @Operation(
    summary = "Returns a map of prisoners in cells within this location",
    description = "Requires role VIEW_PRISONER_LOCATIONS",
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
        description = "Missing required role. Requires the VIEW_PRISONER_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonersInLocationByKey(
    @Schema(description = "Full location key, can be a wing, landing or cell, prisonId must be included", examples = ["MDI-1", "LEI-A-1-001", "PVI-1-2"], required = true)
    @PathVariable
    key: String,
  ) = prisonerLocationService.prisonersInLocations(key)

  @GetMapping("/prison/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('VIEW_PRISONER_LOCATIONS')")
  @Operation(
    summary = "Returns a map of prisoners in cells within this prison",
    description = "Requires role VIEW_PRISONER_LOCATIONS",
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
        description = "Missing required role. Requires the VIEW_PRISONER_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonersInLocationByPrison(
    @Schema(description = "Prison ID", examples = ["MDI", "LEI", "PVI"], required = true)
    @PathVariable
    prisonId: String,
  ) = prisonerLocationService.prisonersInPrison(prisonId = prisonId)
}
