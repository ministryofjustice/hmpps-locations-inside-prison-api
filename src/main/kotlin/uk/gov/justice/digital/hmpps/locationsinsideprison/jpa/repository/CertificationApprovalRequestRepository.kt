package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequest
import java.util.UUID

@Repository
interface CertificationApprovalRequestRepository : JpaRepository<CertificationApprovalRequest, UUID> {
  fun findByPrisonIdAndStatusOrderByRequestedDateDesc(prisonId: String, status: ApprovalRequestStatus): List<CertificationApprovalRequest>
  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CertificationApprovalRequest>

  @Query(
    """
      select r.prisonId as prisonId, count(r) as count
      from CertificationApprovalRequest r
      where r.status = :status
      group by r.prisonId
    """,
  )
  fun countByStatusGroupedByPrison(status: ApprovalRequestStatus): List<PrisonApprovalCount>
}

interface PrisonApprovalCount {
  val prisonId: String
  val count: Long
}
