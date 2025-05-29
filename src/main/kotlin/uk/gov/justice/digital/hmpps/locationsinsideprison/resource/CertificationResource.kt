package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CertificationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
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
) : EventBase() {

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
  ): CertificationApprovalRequestDto = certificationService.requestApproval(
    locationApprovalRequest = locationApprovalRequest,
  )

  @PutMapping("/location/approve")
  @PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
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
    val response = certificationService.approveCertificationRequest(
      approveCertificationRequest = approveCertificationRequest,
    )
    eventPublishAndAudit(
      if (response.newLocation) {
        InternalLocationDomainEventType.LOCATION_CREATED
      } else {
        InternalLocationDomainEventType.LOCATION_AMENDED
      },
    ) {
      response.location
    }
    return response.approvalRequest
  }

  @PutMapping("/location/reject")
  @PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
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
    if (!response.newLocation) {
      eventPublishAndAudit(
        InternalLocationDomainEventType.LOCATION_AMENDED,
      ) {
        response.location
      }
    }
    return response.approvalRequest
  }

  @PutMapping("/location/withdraw")
  @PreAuthorize("hasRole('ROLE_LOCATION_CERTIFICATION')")
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
    if (!response.newLocation) {
      eventPublishAndAudit(
        InternalLocationDomainEventType.LOCATION_AMENDED,
      ) {
        response.location
      }
    }
    return response.approvalRequest
  }
}
