package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("CONVERT_CELL_TO_ROOM")
open class ConvertToNonResidentialCellApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  @Column(name = "converted_cell_type", nullable = true)
  open var convertedCellType: ConvertedCellType,

  @Column(name = "other_converted_cell_type", nullable = true)
  open var otherConvertedCellType: String? = null,

  // Reuses the existing current_in_cell_sanitation column; matches the (un-named) logical column mapping used by
  // SanitationChangeApprovalRequest so Hibernate treats them as the same column.
  @Column(nullable = true)
  open var currentInCellSanitation: Boolean? = null,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
) {
  // The cell is being converted to a non-residential room, so all current capacity, specialist cell types and
  // sanitation are being removed. The "current" values are surfaced for the UI; the pending (new) values are left
  // null so the UI renders e.g. "CNA 1 -> None". Only convertedCellType has a new value (e.g. None -> OFFICE).
  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    convertedCellType = convertedCellType,
    otherConvertedCellType = otherConvertedCellType,
    currentSpecialistCellTypes = getTopLevelLocation()?.getSpecialistCellTypesFromList()?.toSet(),
    currentInCellSanitation = currentInCellSanitation,
  )

  override fun getApprovalType() = ApprovalType.CONVERT_CELL_TO_ROOM
}
