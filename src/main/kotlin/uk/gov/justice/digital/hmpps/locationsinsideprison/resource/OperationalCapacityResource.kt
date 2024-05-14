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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
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
  private val locationService: LocationService
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
  @GetMapping("/signed-op-cap/{id}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get location reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
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
    @Schema(description = "Prison Id", example = "WWI", required = true)
    id: String,
    prisonId: String,
    capacity: Int,
    dateTime: LocalDateTime,
    approvedBy: String,

  ) = null
  }

}
