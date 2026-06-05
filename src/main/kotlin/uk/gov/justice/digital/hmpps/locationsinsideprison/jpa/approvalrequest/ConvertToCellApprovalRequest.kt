package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
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

  // The current (pre-conversion) values are surfaced so the UI can play back "current -> new".
  // Reuses the existing converted_cell_type / other_converted_cell_type columns (see
  // ConvertToNonResidentialCellApprovalRequest) which are otherwise unused for a convert-to-cell.
  @Column(name = "converted_cell_type", nullable = true)
  @Enumerated(EnumType.STRING)
  open var currentConvertedCellType: ConvertedCellType? = null,

  @Column(name = "other_converted_cell_type", nullable = true)
  open var currentOtherConvertedCellType: String? = null,

  // Populated only when the proposed accommodation type / used-for differ from the parent's current
  // values (see Cell.requestApprovalForConvertToCell); null means "matches the parent, nothing to show".
  @Column(name = "current_accommodation_types", nullable = true)
  open var currentAccommodationTypes: String? = null,

  @Column(name = "current_used_for_types", nullable = true)
  open var currentUsedForTypes: String? = null,

  // The proposed door number (cell mark) / in-cell sanitation for the re-created cell, plus the room's
  // existing values so the UI can play back "current -> new". These reuse the shared cell_mark /
  // current_cell_mark / in_cell_sanitation / current_in_cell_sanitation columns (see
  // CellMarkChangeApprovalRequest / SanitationChangeApprovalRequest); the property names must match those
  // siblings so Hibernate maps a single logical name onto each shared physical column.
  open var cellMark: String? = null,

  open var currentCellMark: String? = null,

  open var inCellSanitation: Boolean? = null,

  open var currentInCellSanitation: Boolean? = null,

  topLevelAccommodationTypes: String? = null,

  topLevelUsedFor: String? = null,

) : LocationCertificationApprovalRequest(
  id = id,
  location = location,
  locationKey = location.getKey(),
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
  topLevelAccommodationTypes = topLevelAccommodationTypes,
  topLevelUsedFor = topLevelUsedFor,
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

  fun getCurrentAccommodationTypesFromList(): List<AccommodationType> = currentAccommodationTypes
    ?.split(",")
    ?.filter { it.isNotBlank() }
    ?.map { AccommodationType.valueOf(it.trim()) }
    ?: emptyList()

  fun getCurrentUsedForTypesFromList(): List<UsedForType> = currentUsedForTypes
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
    currentConvertedCellType = currentConvertedCellType,
    currentOtherConvertedCellType = currentOtherConvertedCellType,
    currentAccommodationTypes = getCurrentAccommodationTypesFromList().takeIf { it.isNotEmpty() },
    currentUsedForTypes = getCurrentUsedForTypesFromList().takeIf { it.isNotEmpty() },
    cellMark = cellMark,
    currentCellMark = currentCellMark,
    inCellSanitation = inCellSanitation,
    currentInCellSanitation = currentInCellSanitation,
  )

  override fun getApprovalType() = ApprovalType.CONVERT_ROOM_TO_CELL
}
