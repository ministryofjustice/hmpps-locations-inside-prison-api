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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.OperationalCapacityDto
import java.time.LocalDateTime

/**
 * MAP-978 Support Signed Operational Capacity
 * @author marcus.aleman
 *
 *  Methods : GET & POST
 * {
 *   "signedOperationCapacity": 342,
 *   "approvedBy": "MALEMAN"
 * }
 *
 */
@RestController
@Validated
@RequestMapping("/signed-op-cap", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Operational Capacity",
  description = "Returns signed operational capacity data per prison.",
)
class OperationalCapacityResource() : EventBaseResource() {

  @GetMapping("/{prisonId}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')") // todo Need create a New Role?
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
  ): OperationalCapacityDto? = OperationalCapacityDto(prisonId = "MDI", approvedBy = "MALEMAN", capacity = 100, dateTime = LocalDateTime.now())


  @PostMapping("/{prisonId}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Post Operation Capacity",
    description = "Requires role ROLE_MAINTAIN_LOCATIONS",
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
        description = "Missing required role. Requires the ROLE_MAINTAIN_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "PrisonID not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun postOperationalCapacity(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @PathVariable
    prisonId: String,
  ): OperationalCapacityDto? = OperationalCapacityDto(prisonId = "MDI", approvedBy = "MALEMAN", capacity = 100, dateTime = LocalDateTime.now())

}