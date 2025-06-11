package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Certification Approval Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CertificationApprovalRequestDto(
  @Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Location key", example = "MDI-A-1-001", required = true)
  val locationKey: String,

  @Schema(description = "Status of the approval request", example = "PENDING", required = true)
  val status: ApprovalRequestStatus,

  @Schema(description = "User who requested the approval", example = "USER1", required = true)
  val requestedBy: String,

  @Schema(description = "Date and time of the request", required = true)
  val requestedDate: LocalDateTime,

  @Schema(description = "User who approved or rejected the request", example = "USER2", required = false)
  val approvedOrRejectedBy: String? = null,

  @Schema(description = "Date and time of the approval or rejection", required = false)
  val approvedOrRejectedDate: LocalDateTime? = null,

  @Schema(description = "Comments about the approval or rejection", required = false)
  val comments: String? = null,

  @Schema(description = "Change in certified normal accommodation", example = "1", required = true)
  val certifiedNormalAccommodationChange: Int = 0,

  @Schema(description = "Change in working capacity", example = "1", required = true)
  val workingCapacityChange: Int = 0,

  @Schema(description = "Change in maximum capacity", example = "1", required = true)
  val maxCapacityChange: Int = 0,

  @Schema(description = "Locations affected by the approval", required = false)
  val locations: List<CertificationApprovalRequestLocationDto>? = null,
)

@Schema(description = "Request to approve a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApproveCertificationRequestDto(
  @Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,

  @Schema(description = "Comments about the approval", required = true)
  val comments: String,
)

@Schema(description = "Request to reject a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RejectCertificationRequestDto(
  @Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,

  @Schema(description = "Comments about the rejection", required = true)
  val comments: String,
)

@Schema(description = "Request to withdraw a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WithdrawCertificationRequestDto(
  @Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,

  @Schema(description = "Comments about the withdrawal", required = true)
  val comments: String,
)

@Schema(description = "Response from approving/rejecting a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApprovalResponse(
  val newLocation: Boolean = false,
  val location: Location,
  val approvalRequest: CertificationApprovalRequestDto,
)
