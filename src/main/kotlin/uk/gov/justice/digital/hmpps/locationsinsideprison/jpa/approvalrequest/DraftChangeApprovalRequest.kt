package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("DRAFT")
open class DraftChangeApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,
  certifiedNormalAccommodationChange: Int,
  workingCapacityChange: Int,
  maxCapacityChange: Int,
) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  certifiedNormalAccommodationChange = certifiedNormalAccommodationChange,
  workingCapacityChange = workingCapacityChange,
  maxCapacityChange = maxCapacityChange,
) {

  override fun getApprovalType() = ApprovalType.DRAFT
}
