package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertificationApprovalRequest
import java.util.UUID

@Repository
interface CertificationApprovalRequestRepository : JpaRepository<CertificationApprovalRequest, UUID> {
  fun findByPrisonIdAndStatusOrderByRequestedDateDesc(prisonId: String, status: ApprovalRequestStatus): List<CertificationApprovalRequest>
  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CertificationApprovalRequest>
}
