package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("CELL_SANITATION")
open class SanitationChangeApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  var inCellSanitation: Boolean,

  @Column(nullable = true)
  private var currentInCellSanitation: Boolean? = null,
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
    inCellSanitation = inCellSanitation,
    currentInCellSanitation = currentInCellSanitation,
  )

  override fun getApprovalType() = ApprovalType.CELL_SANITATION
}
