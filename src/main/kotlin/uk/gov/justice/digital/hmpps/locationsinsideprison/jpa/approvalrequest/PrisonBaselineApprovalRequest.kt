package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("PRISON_BASELINE")
open class PrisonBaselineApprovalRequest(
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

  override fun getApprovalType() = ApprovalType.PRISON_BASELINE
}
