package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Certification Approval Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CertificationApprovalRequestDto(
  @param:Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @param:Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = false)
  val locationId: UUID? = null,

  @param:Schema(description = "Type of approval", example = "SIGNED_OP_CAP", required = true)
  val approvalType: ApprovalType,

  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Location key", example = "MDI-A-1-001", required = false)
  val locationKey: String? = null,

  @param:Schema(description = "Status of the approval request", example = "PENDING", required = true)
  val status: ApprovalRequestStatus,

  @param:Schema(description = "User who requested the approval", example = "USER1", required = true)
  val requestedBy: String,

  @param:Schema(description = "Date and time of the request", required = true)
  val requestedDate: LocalDateTime,

  @param:Schema(description = "User who approved or rejected the request", example = "USER2", required = false)
  val approvedOrRejectedBy: String? = null,

  @param:Schema(description = "Date and time of the approval or rejection", required = false)
  val approvedOrRejectedDate: LocalDateTime? = null,

  @param:Schema(description = "Comments about the approval or rejection", required = false)
  val comments: String? = null,

  @param:Schema(description = "Change in certified normal accommodation", example = "1", required = true)
  val certifiedNormalAccommodationChange: Int? = null,

  @param:Schema(description = "Change in working capacity", example = "1", required = true)
  val workingCapacityChange: Int? = null,

  @param:Schema(description = "Change in maximum capacity", example = "1", required = true)
  val maxCapacityChange: Int? = null,

  @param:Schema(description = "Current value of signed operational capacity", example = "1", required = true)
  val currentSignedOperationCapacity: Int? = null,

  @param:Schema(description = "Change signed operational capacity", example = "1", required = true)
  val signedOperationCapacityChange: Int? = null,

  @param:Schema(description = "The reason why the signed op cap was changed", example = "Change in number of cells", required = false)
  @Deprecated("Use reasonForChange instead")
  val reasonForSignedOpChange: String? = null,

  @param:Schema(description = "The reason why the approval was requested", example = "Change in number of cells", required = false)
  val reasonForChange: String? = null,

  @param:Schema(description = "Locations affected by the approval", required = false)
  val locations: List<CertificationApprovalRequestLocationDto>? = null,

  @param:Schema(description = "The ID of the certificate once approved", required = false)
  val certificateId: UUID? = null,

  @param:Schema(description = "Cell mark of the location", required = false, example = "A1", minLength = 1)
  val cellMark: String? = null,

  @param:Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @param:Schema(
    description = "For OTHER deactivation reason, a free text comment is provided",
    example = "Window damage",
    required = false,
  )
  val deactivationReasonDescription: String? = null,

  @param:Schema(description = "Estimated reactivation date for location reactivation", example = "2026-01-24", required = false)
  val proposedReactivationDate: LocalDate? = null,

  @param:Schema(description = "Planet FM reference number", example = "2323/45M", required = false)
  val planetFmReference: String? = null,
)

@Schema(description = "Request to approve a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApproveCertificationRequestDto(
  @param:Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,
)

@Schema(description = "Request to reject a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RejectCertificationRequestDto(
  @param:Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,

  @param:Schema(description = "Comments about the rejection", required = true)
  val comments: String,
)

@Schema(description = "Request to withdraw a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WithdrawCertificationRequestDto(
  @param:Schema(description = "Approval request reference", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val approvalRequestReference: UUID,

  @param:Schema(description = "Comments about the withdrawal", required = true)
  val comments: String,
)

@Schema(description = "Response from approving/rejecting a certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApprovalResponse(
  val approvalRequest: CertificationApprovalRequestDto,
  val newLocation: Boolean = false,
  val prisonId: String,
  val location: Location? = null,
)
