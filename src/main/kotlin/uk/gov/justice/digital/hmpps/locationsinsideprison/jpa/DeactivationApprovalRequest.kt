package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("DEACTIVATION")
open class DeactivationApprovalRequest(
  id: UUID? = null,
  location: ResidentialLocation,
  requestedBy: String,
  requestedDate: LocalDateTime,
  workingCapacityChange: Int,
  reasonForChange: String? = null,

  val deactivatedReason: DeactivatedReason,
  val deactivationReasonDescription: String? = null,
  val proposedReactivationDate: LocalDate? = null,
  val planetFmReference: String? = null,

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
  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    deactivatedReason = deactivatedReason,
    deactivationReasonDescription = deactivationReasonDescription,
    proposedReactivationDate = proposedReactivationDate,
    planetFmReference = planetFmReference,
  )

  override fun getApprovalType() = ApprovalType.DEACTIVATION
}
