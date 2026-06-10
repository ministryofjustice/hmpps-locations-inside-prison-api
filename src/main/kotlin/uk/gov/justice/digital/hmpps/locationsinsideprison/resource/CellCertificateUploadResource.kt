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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadStatusFilter
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadService
import java.util.UUID

@RestController
@Validated
@RequestMapping("/locations/bulk", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Bulk Location Operations",
  description = "Performs bulk operations",
)
class CellCertificateUploadResource(
  private val cellCertificateUploadService: CellCertificateUploadService,
) {

  @PostMapping("update-cell-certificate/{prisonId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Upload a cell certificate for a prison and start asynchronous processing",
    description = "Stores the supplied cell capacities, cell marks and sanitation for the prison and queues " +
      "them for background processing. Returns an identifier that can be used to monitor progress. " +
      "Requires role MAINTAIN_LOCATIONS and write scope.",
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Upload accepted and queued for processing",
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
        description = "Prison not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "A cell certificate upload is already in progress for this prison",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun uploadCellCertificate(
    @Schema(description = "Prison ID", example = "MDI", required = true)
    @PathVariable prisonId: String,
    @RequestBody @Validated updateCapacityRequest: UpdateCapacityRequest,
  ): CellCertificateUploadDto = cellCertificateUploadService.requestCellCertificateUpload(prisonId, updateCapacityRequest)

  @GetMapping("update-cell-certificate/{prisonId}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS')")
  @Operation(
    summary = "List cell certificate uploads for a prison",
    description = "Returns the upload history for a prison (most recent first), optionally filtered to those " +
      "still PROCESSING or those that are COMPLETE. Each entry summarises status, timings and record counts. " +
      "Requires role MAINTAIN_LOCATIONS.",
    responses = [
      ApiResponse(responseCode = "200", description = "Returns the list of uploads for the prison"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCellCertificateUploads(
    @Schema(description = "Prison ID", example = "MDI", required = true)
    @PathVariable prisonId: String,
    @Schema(description = "Optional filter", example = "PROCESSING", required = false)
    @RequestParam(required = false) status: CellCertificateUploadStatusFilter?,
  ): List<CellCertificateUploadDto> = cellCertificateUploadService.getCellCertificateUploads(prisonId, status)

  @GetMapping("update-cell-certificate/upload/{uploadId}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS')")
  @Operation(
    summary = "Get a single cell certificate upload with its per-cell results",
    description = "Returns the upload summary plus each cell's result (status, dates and the values changed " +
      "from/to) for drilling into a running or completed upload. Requires role MAINTAIN_LOCATIONS.",
    responses = [
      ApiResponse(responseCode = "200", description = "Returns the upload and its per-cell results"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Cell certificate upload not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getCellCertificateUpload(
    @Schema(description = "Upload ID", example = "01912e1e-0000-7000-8000-000000000000", required = true)
    @PathVariable uploadId: UUID,
  ): CellCertificateUploadDto = cellCertificateUploadService.getCellCertificateUpload(uploadId)
}
