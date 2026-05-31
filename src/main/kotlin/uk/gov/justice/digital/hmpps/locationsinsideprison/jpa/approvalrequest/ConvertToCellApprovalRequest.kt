package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorValue("CONVERT_ROOM_TO_CELL")
open class ConvertToCellApprovalRequest(
  id: UUID? = null,
  location: Cell,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  @Column(name = "accommodation_type", nullable = true)
  @Enumerated(EnumType.STRING)
  open var accommodationType: AccommodationType,

  // Reuses the shared pending specialist cell types column (see SpecialistCellTypeChangeApprovalRequest).
  @Column(name = "pending_specialist_cell_types", nullable = true)
  open var specialistCellTypes: String? = null,

  @Column(name = "used_for_types", nullable = true)
  open var usedForTypes: String? = null,

  // Reuses the shared capacity columns (see CapacityChangeApprovalRequest).
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
    ?.split(",")
    ?.filter { it.isNotBlank() }
    ?.map { SpecialistCellType.valueOf(it.trim()) }
    ?: emptyList()

  fun getUsedForTypesFromPendingList(): List<UsedForType> = usedForTypes
    ?.split(",")
    ?.filter { it.isNotBlank() }
    ?.map { UsedForType.valueOf(it.trim()) }
    ?: emptyList()

  // The non-residential room is being converted back to a cell. The "new" (pending) values are the cell being
  // re-created; the room currently has no capacity or specialist cell types so the UI renders e.g. "None -> 2".
  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    workingCapacity = workingCapacity,
    maxCapacity = maxCapacity,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
    specialistCellTypes = getSpecialistCellTypesFromPendingList().toSet().takeIf { it.isNotEmpty() },
    accommodationType = accommodationType,
    usedForTypes = getUsedForTypesFromPendingList().takeIf { it.isNotEmpty() },
  )

  override fun getApprovalType() = ApprovalType.CONVERT_ROOM_TO_CELL
}
