package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne
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

  @Column
  private val reasonForChange: String,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  private val signedOperationCapacity: SignedOperationCapacity,

  @Column(nullable = false)
  private var currentSignedOperationCapacity: Int = 0,

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
    reasonForSignedOpChange = reasonForChange,
    currentSignedOperationCapacity = currentSignedOperationCapacity,
    signedOperationCapacityChange = signedOperationCapacityChange,
  )

  override fun approve(
    approvedBy: String,
    approvedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ) {
    super.approve(approvedBy, approvedDate, linkedTransaction)
    signedOperationCapacity.signedOperationCapacity += signedOperationCapacityChange
  }
}
