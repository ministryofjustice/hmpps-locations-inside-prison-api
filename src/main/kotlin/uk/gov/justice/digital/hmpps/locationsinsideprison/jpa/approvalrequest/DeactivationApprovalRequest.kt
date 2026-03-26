package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
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

  open val deactivatedReason: DeactivatedReason,
  open val deactivationReasonDescription: String? = null,
  open val proposedReactivationDate: LocalDate? = null,
  open val planetFmReference: String? = null,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  workingCapacityChange = workingCapacityChange,
) {
  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    deactivatedReason = deactivatedReason,
    deactivationReasonDescription = deactivationReasonDescription,
    proposedReactivationDate = proposedReactivationDate,
    planetFmReference = planetFmReference,
  )

  override fun getApprovalType() = ApprovalType.DEACTIVATION
}
