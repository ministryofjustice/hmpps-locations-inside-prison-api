package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("CAPACITY_CHANGE")
open class CapacityChangeApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  @Column(nullable = true)
  var workingCapacity: Int? = null,

  @Column(nullable = true)
  var maxCapacity: Int? = null,

  @Column(nullable = true)
  var certifiedNormalAccommodation: Int? = null,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  locations = sortedSetOf(location.toCertificationApprovalRequestLocation(includePending = true)),
) {
  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    workingCapacity = workingCapacity,
    maxCapacity = maxCapacity,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
  )

  override fun getApprovalType() = ApprovalType.CAPACITY_CHANGE
}
