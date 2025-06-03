package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateService
import java.util.UUID

@RestController
@Validated
@RequestMapping("/cell-certificates", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Cell Certificates", description = "Cell Certificate Management")
@PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
class CellCertificateResource(
  private val cellCertificateService: CellCertificateService,
) {

  @GetMapping("/{id}")
  @Operation(
    summary = "Get a cell certificate by ID",
    description = "Returns a cell certificate by ID",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Cell certificate found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = CellCertificateDto::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Cell certificate not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  fun getCellCertificate(
    @Parameter(description = "Cell certificate ID", required = true)
    @PathVariable
    id: UUID,
  ): CellCertificateDto = cellCertificateService.getCellCertificate(id)

  @GetMapping("/prison/{prisonId}")
  @Operation(
    summary = "Get all cell certificates for a prison",
    description = "Returns all cell certificates for a prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Cell certificates found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = CellCertificateDto::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  fun getCellCertificatesForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable("prisonId")
    prisonId: String,
  ): List<CellCertificateDto> = cellCertificateService.getCellCertificatesForPrison(prisonId)

  @GetMapping("/prison/{prisonId}/current")
  @Operation(
    summary = "Get the current cell certificate for a prison",
    description = "Returns the current cell certificate for a prison",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Current cell certificate found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = CellCertificateDto::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json")],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No current cell certificate found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  fun getCurrentCellCertificateForPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable("prisonId")
    prisonId: String,
  ): CellCertificateDto = cellCertificateService.getCurrentCellCertificateForPrison(prisonId)
}
