package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
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
) {

  override fun getApprovalType() = ApprovalType.REACTIVATION

  override fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction, clock: Clock) {
    val locationsReactivated = mutableSetOf<Location>()
    val amendedLocations = mutableSetOf<Location>()
    location.reactivate(
      userOrSystemInContext = requestedBy,
      clock = clock,
      linkedTransaction = linkedTransaction,
      reactivatedLocations = locationsReactivated,
      maxCapacity = 0,
      workingCapacity = 0,
      amendedLocations = amendedLocations,
    )
    super.approve(approvedBy, approvedDate, linkedTransaction, clock)
  }
}
