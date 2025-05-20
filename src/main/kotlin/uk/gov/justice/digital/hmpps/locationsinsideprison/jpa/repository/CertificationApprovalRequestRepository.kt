package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import java.util.UUID

@Repository
interface CertificationApprovalRequestRepository : JpaRepository<CertificationApprovalRequest, UUID> {
  fun findByLocationAndStatusOrderByRequestedDateDesc(location: Location, status: ApprovalRequestStatus): List<CertificationApprovalRequest>
  fun findByLocationOrderByRequestedDateDesc(location: Location): List<CertificationApprovalRequest>
  fun findByStatus(status: ApprovalRequestStatus): List<CertificationApprovalRequest>
}
