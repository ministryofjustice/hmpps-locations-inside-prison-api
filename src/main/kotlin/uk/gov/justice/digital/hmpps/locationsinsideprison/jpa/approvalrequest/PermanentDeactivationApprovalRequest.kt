package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("PERMANENT_DEACTIVATION")
open class PermanentDeactivationApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,
  workingCapacityChange: Int = 0,
) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  workingCapacityChange = workingCapacityChange,
) {
  override fun getApprovalType() = ApprovalType.PERMANENT_DEACTIVATION
}
