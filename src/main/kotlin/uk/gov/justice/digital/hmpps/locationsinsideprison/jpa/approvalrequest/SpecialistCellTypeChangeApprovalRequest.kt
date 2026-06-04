package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("SPECIALIST_CELL_TYPE")
open class SpecialistCellTypeChangeApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  @Column(name = "pending_specialist_cell_types", nullable = true)
  open var specialistCellTypes: String,

  @Column(nullable = true)
  open var workingCapacity: Int,

  @Column(nullable = true)
  open var maxCapacity: Int,

  @Column(nullable = true)
  open var certifiedNormalAccommodation: Int,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
) {
  fun getSpecialistCellTypesFromPendingList(): List<SpecialistCellType> = specialistCellTypes
    .split(",")
    .filter { it.isNotBlank() }
    .map { SpecialistCellType.valueOf(it.trim()) }

  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    specialistCellTypes = getSpecialistCellTypesFromPendingList().toSet(),
    currentSpecialistCellTypes = getTopLevelLocation()?.getCurrentSpecialistCellTypesFromList()?.toSet(),
    workingCapacity = workingCapacity,
    maxCapacity = maxCapacity,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
  )

  override fun getApprovalType() = ApprovalType.SPECIALIST_CELL_TYPE
}
