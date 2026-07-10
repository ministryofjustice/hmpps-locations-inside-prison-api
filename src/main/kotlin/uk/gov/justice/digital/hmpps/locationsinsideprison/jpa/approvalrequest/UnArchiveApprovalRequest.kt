package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.LocalDateTime
import java.util.UUID

/**
 * Audit record of a location being un-archived (restored from an archive to temporarily inactive). The un-archive
 * itself requires no human decision, but for prisons where certification approval is active the restore is recorded
 * as an auto-approved request so the regenerated cell certificate has a corresponding approval trail
 * (see ApprovalDecisionService.unarchiveWithApproval).
 */
@Entity
@DiscriminatorValue("UN_ARCHIVE")
open class UnArchiveApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,
) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
) {
  override fun getApprovalType() = ApprovalType.UN_ARCHIVE
}
