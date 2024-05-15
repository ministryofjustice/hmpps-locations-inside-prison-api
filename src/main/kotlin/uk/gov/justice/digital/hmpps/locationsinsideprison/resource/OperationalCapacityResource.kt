package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.OperationalCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.OperationalCapacityService
import java.time.LocalDateTime

@RestController
@Validated
@RequestMapping("/signed-op-cap", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Operational Capacity",
  description = "Returns signed operational capacity data per prison.",
)
class OperationalCapacityResource(
  private val reactiveUserDetailsService: MapReactiveUserDetailsService,
  private val locationService: LocationService,
  private val operationalCapacityService: OperationalCapacityService
) : EventBaseResource() {

  /**
   * MAP-978 Support Signed Operational Capacity
   * @author marcus.aleman
   *
   * {
   *   "signedOperationCapacity": 342,
   *   "approvedBy": "MALEMAN"
   * }
   *
   */
  @GetMapping("/{prisonId}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')") //todo Need create a New Role?
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get Operation Capacity",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns Operation Capacity data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the ROLE_VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "PrisonID not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getOperationalCapacity(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @PathVariable
    prisonId: String,
    ): OperationalCapacityDto? = OperationalCapacityDto(prisonId="MDI", approvedBy = "MALEMAN", capacity = 100, dateTime = LocalDateTime.now())
}