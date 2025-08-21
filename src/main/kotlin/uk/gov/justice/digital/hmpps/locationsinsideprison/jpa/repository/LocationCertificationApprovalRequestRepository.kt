package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationCertificationApprovalRequest
import java.util.UUID

@Repository
interface LocationCertificationApprovalRequestRepository : JpaRepository<LocationCertificationApprovalRequest, UUID> {
  fun findByLocationKeyOrderByRequestedDateDesc(locationKey: String): List<LocationCertificationApprovalRequest>
}
