package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonCertificationApprovalRequest
import java.util.UUID

@Repository
interface PrisonCertificationApprovalRequestRepository : JpaRepository<PrisonCertificationApprovalRequest, UUID> {
  fun findByPrisonIdAndStatusOrderByRequestedDateDesc(prisonId: String, status: ApprovalRequestStatus): List<PrisonCertificationApprovalRequest>
  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<PrisonCertificationApprovalRequest>
}
