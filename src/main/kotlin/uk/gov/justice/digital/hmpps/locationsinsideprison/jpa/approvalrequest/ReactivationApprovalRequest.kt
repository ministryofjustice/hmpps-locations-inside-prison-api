package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
@DiscriminatorValue("REACTIVATION")
open class ReactivationApprovalRequest(
  id: UUID? = null,
  prisonId: String,
  status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,
  requestedBy: String,
  requestedDate: LocalDateTime,
  approvedOrRejectedBy: String? = null,
  approvedOrRejectedDate: LocalDateTime? = null,
  reasonForChange: String? = null,
  comments: String? = null,
  locations: SortedSet<CertificationApprovalRequestLocation>,

) : PrisonLevelApprovalRequest(
  id = id,
  prisonId = prisonId,
  status = status,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  approvedOrRejectedBy = approvedOrRejectedBy,
  approvedOrRejectedDate = approvedOrRejectedDate,
  reasonForChange = reasonForChange,
  comments = comments,
  locations = locations,
) {
  override fun getApprovalType() = ApprovalType.REACTIVATION
}
