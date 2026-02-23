package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("REACTIVATION")
open class ReactivationApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  workingCapacityChange: Int,
  reasonForChange: String? = null,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  workingCapacityChange = workingCapacityChange,
  locations = sortedSetOf(location.toCertificationApprovalRequestLocation(includePending = true)),
) {

  override fun getApprovalType() = ApprovalType.REACTIVATION

  override fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    super.approve(approvedBy, approvedDate, linkedTransaction)
  }
}
