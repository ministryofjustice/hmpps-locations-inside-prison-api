package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("CELL_MARK")
open class CellMarkChangeApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  var cellMark: String,
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
    cellMark = cellMark,
  )

  override fun getApprovalType() = ApprovalType.CELL_MARK
}
