package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
@DiscriminatorValue("REACTIVATION")
open class ReactivationApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  workingCapacityChange: Int,
  maxCapacityChange: Int,
  certifiedNormalAccommodationChange: Int,
  locations: SortedSet<CertificationApprovalRequestLocation>,

  open val cascadeReactivation: Boolean = false,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  workingCapacityChange = workingCapacityChange,
  maxCapacityChange = maxCapacityChange,
  certifiedNormalAccommodationChange = certifiedNormalAccommodationChange,
  locations = locations,
) {
  override fun getApprovalType() = ApprovalType.REACTIVATION
}
