package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CertificationService
import java.util.*

@RestController
@Validated
@RequestMapping("/certification", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Certification",
  description = "Functionality to manage certification of residential locations",
)
class CertificationResource(
  private val certificationService: CertificationService,
) : EventBaseResource() {

  @PutMapping("/location/request-approval")
  @PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
  @Operation(
    summary = "Requests approval for a location currently either LOCKED or in DRAFT status, locations below this will be included in the request",
    description = "Requires role LOCATION_CERTIFICATION",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the approval request status",
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
        description = "Missing required role. Requires the LOCATION_CERTIFICATION role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun requestApproval(
    @RequestBody
    @Validated
    locationApprovalRequest: LocationApprovalRequest,
  ): Location = certificationService.requestApproval(
    locationApprovalRequest = locationApprovalRequest,
  )
}

@Schema(description = "Request to approve a set of locations")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 30, message = "Description must be less than 31 characters")
  val localName: String? = null,

  @Schema(description = "Username of the staff updating the location", required = false)
  val updatedBy: String? = null,
)
