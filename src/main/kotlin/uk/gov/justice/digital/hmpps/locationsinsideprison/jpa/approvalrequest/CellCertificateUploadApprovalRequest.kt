package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

/**
 * Backs the cell certificate generated from a cell certificate upload. Behaves like a baseline request -
 * it does not change location data on approval (the upload applies capacities directly) - but is recorded
 * with its own type so upload-generated certificates are distinguishable in the audit trail.
 */
@Entity
@DiscriminatorValue("CELL_CERT_UPLOAD")
open class CellCertificateUploadApprovalRequest(
  id: UUID? = null,
  prisonId: String,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String,
) : CertificationApprovalRequest(
  id = id,
  prisonId = prisonId,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
) {

  override fun getApprovalType() = ApprovalType.CELL_CERTIFICATE_UPLOAD
}
