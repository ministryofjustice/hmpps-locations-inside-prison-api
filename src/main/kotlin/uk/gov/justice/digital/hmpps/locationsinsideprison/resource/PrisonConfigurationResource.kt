package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonConfigurationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialStatus

@RestController
@Validated
@RequestMapping("/prison-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Prison Configuration",
  description = "Allows views and updates on prison configuration",
)
class PrisonConfigurationResource(
  private val prisonConfigurationService: PrisonConfigurationService,
) {

  @PutMapping("/{prisonId}/resi-service/{status}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Update the status of the service for a prison",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns configuration",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateResiLocationServiceActiveStatus(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Status of the resi service to change", example = "ACTIVE", required = true)
    @PathVariable
    status: ResidentialStatus,
  ) = prisonConfigurationService.updateResiLocationServiceActiveStatus(prisonId, status)

  @PutMapping("/{prisonId}/certification-approval-required/{approvalProcessStatus}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Update the certification approval process for a prison",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns configuration",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateCertificationApprovalProcessForPrison(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Activate/Deactivate the certification approval process for this prison", example = "ACTIVE", required = true)
    @PathVariable
    approvalProcessStatus: ResidentialStatus,
  ) = prisonConfigurationService.updateCertificationApprovalProcess(prisonId, approvalProcessStatus)

  @PutMapping("/{prisonId}/include-seg-in-roll-count/{includeSegInRollCountStatus}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Update seg in roll count status",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns configuration",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateIncludeSegInRollCount(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Activate/Deactivate include seg in roll count for this prison", example = "ACTIVE", required = true)
    @PathVariable
    includeSegInRollCountStatus: ResidentialStatus,
  ) = prisonConfigurationService.updateIncludeSegInRollCount(prisonId, includeSegInRollCountStatus)

  @GetMapping("/{prisonId}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Get prison configuration",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns configuration",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPrisonConfiguration(
    @Schema(description = "Prison ID", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
  ) = prisonConfigurationService.getPrisonConfiguration(prisonId)
}
