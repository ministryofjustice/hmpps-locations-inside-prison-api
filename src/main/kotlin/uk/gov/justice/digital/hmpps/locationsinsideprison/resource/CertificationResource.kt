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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ApprovalRequestService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CertificationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import java.util.*

@RestController
@Validated
@PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
@RequestMapping("/certification", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Certification",
  description = "Functionality to manage certification of residential locations",
)
class CertificationResource(
  private val certificationService: CertificationService,
  private val approvalRequestService: ApprovalRequestService,
) : EventBase() {

  @PutMapping("/location/request-approval")
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
  ): CertificationApprovalRequestDto = certificationService.requestApproval(
    locationApprovalRequest = locationApprovalRequest,
  )

  @PutMapping("/location/approve")
  @Operation(
    summary = "Approves a certification request for a location",
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
        description = "Approval request not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun approveCertificationRequest(
    @RequestBody
    @Validated
    approveCertificationRequest: ApproveCertificationRequestDto,
  ): CertificationApprovalRequestDto {
    val approvalResponse = certificationService.approveCertificationRequest(
      approveCertificationRequest = approveCertificationRequest,
    )
    approvalResponse.location?.let { publishedLocation ->
      eventPublishAndAudit(
        if (approvalResponse.newLocation) {
          InternalLocationDomainEventType.LOCATION_CREATED
        } else {
          InternalLocationDomainEventType.LOCATION_AMENDED
        },
      ) {
        publishedLocation
      }
    }
    return approvalResponse.approvalRequest
  }

  @PutMapping("/location/reject")
  @Operation(
    summary = "Rejects a certification request for a location",
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
        description = "Approval request not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun rejectCertificationRequest(
    @RequestBody
    @Validated
    rejectCertificationRequest: RejectCertificationRequestDto,
  ): CertificationApprovalRequestDto {
    val response = certificationService.rejectCertificationRequest(
      rejectCertificationRequest = rejectCertificationRequest,
    )
    if (!response.newLocation && response.location != null) {
      eventPublishAndAudit(
        InternalLocationDomainEventType.LOCATION_AMENDED,
      ) {
        response.location
      }
    }
    return response.approvalRequest
  }

  @PutMapping("/location/withdraw")
  @Operation(
    summary = "Withdraw a certification request for a location",
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
        description = "Approval request not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun withdrawCertificationRequest(
    @RequestBody
    @Validated
    withdrawCertificationRequest: WithdrawCertificationRequestDto,
  ): CertificationApprovalRequestDto {
    val response = certificationService.withdrawCertificationRequest(
      withdrawCertificationRequest = withdrawCertificationRequest,
    )
    if (!response.newLocation && response.location != null) {
      eventPublishAndAudit(
        InternalLocationDomainEventType.LOCATION_AMENDED,
      ) {
        response.location
      }
    }
    return response.approvalRequest
  }

  @GetMapping("/request-approvals/prison/{prisonId}")
  @Operation(
    summary = "Get all certification approval requests with optional filtering",
    description = "Requires role LOCATION_CERTIFICATION",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns a list of certification approval requests",
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
    ],
  )
  fun getApprovalRequests(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable("prisonId")
    prisonId: String,
    @RequestParam(name = "status", required = false, defaultValue = "PENDING")
    status: ApprovalRequestStatus? = ApprovalRequestStatus.PENDING,
  ): List<CertificationApprovalRequestDto> = approvalRequestService.getApprovalRequests(prisonId, status)

  @GetMapping("/request-approvals/{id}")
  @PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
  @Operation(
    summary = "Get a certification approval request by ID",
    description = "Requires role LOCATION_CERTIFICATION",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the certification approval request",
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
        description = "Approval request not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getApprovalRequest(
    @Parameter(description = "Approval request ID", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
    @PathVariable
    id: UUID,
  ): CertificationApprovalRequestDto = approvalRequestService.getApprovalRequest(id)
}
