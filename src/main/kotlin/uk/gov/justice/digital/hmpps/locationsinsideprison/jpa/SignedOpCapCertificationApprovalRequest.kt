package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("SIGNED_OP_CAP_APPROVAL_REQUEST")
open class SignedOpCapCertificationApprovalRequest(
  id: UUID? = null,
  approvalType: ApprovalType,
  prisonId: String,
  status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,
  requestedBy: String,
  requestedDate: LocalDateTime,
  approvedOrRejectedBy: String? = null,
  approvedOrRejectedDate: LocalDateTime? = null,
  comments: String? = null,

  @Column(nullable = false)
  private var signedOperationCapacityChange: Int = 0,

) : CertificationApprovalRequest(
  id = id,
  approvalType = approvalType,
  prisonId = prisonId,
  status = status,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  approvedOrRejectedBy = approvedOrRejectedBy,
  approvedOrRejectedDate = approvedOrRejectedDate,
  comments = comments,
) {
  override fun toDto(showLocations: Boolean) = super.toDto(showLocations).copy(
    signedOperationCapacityChange = signedOperationCapacityChange,
  )

  override fun approve(
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
    comments: String,
  ) {
    super.approve(approvedBy, approvedDate, linkedTransaction, comments)
    signedOperationCapacityChange = 0
  }

  override fun reject(
    rejectedBy: String,
    rejectedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
    comments: String,
  ) {
    super.reject(rejectedBy, rejectedDate, linkedTransaction, comments)
    signedOperationCapacityChange = 0
  }

  override fun withdraw(
    withdrawnBy: String,
    withdrawnDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
    comments: String,
  ) {
    super.withdraw(withdrawnBy, withdrawnDate, linkedTransaction, comments)
    signedOperationCapacityChange = 0
  }
}
