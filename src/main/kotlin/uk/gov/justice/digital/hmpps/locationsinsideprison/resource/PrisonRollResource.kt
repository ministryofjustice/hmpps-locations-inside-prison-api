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
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonRollCount
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonRollCountService

@RestController
@Validated
@RequestMapping("/prison/roll-count/{prisonId}", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Prison roll count",
  description = "Returns prison roll count information",
)
class PrisonRollResource(
  private val prisonRollCountService: PrisonRollCountService,
) {

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Roll count for a specific prison",
    description = "Requires role ESTABLISHMENT_ROLL",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns roll count for a specific prison",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the ESTABLISHMENT_ROLL role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @PreAuthorize("hasRole('ESTABLISHMENT_ROLL')")
  fun getPrisonRollCount(
    @Schema(
      description = "Prison Id",
      example = "MDI",
      required = true,
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @PathVariable
    prisonId: String,
  ): PrisonRollCount =
    prisonRollCountService.getPrisonRollCount(prisonId)
}
