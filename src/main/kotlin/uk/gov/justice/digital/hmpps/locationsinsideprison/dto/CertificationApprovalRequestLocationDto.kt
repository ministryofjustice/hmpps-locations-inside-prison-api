package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertificationApprovalRequestLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import java.util.UUID

@Schema(description = "Location affected by certification approval")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CertificationApprovalRequestLocationDto(
  @Schema(description = "Location ID", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Location code", example = "MDI-A-1-001", required = true)
  val locationCode: String,

  @Schema(description = "Cell mark", example = "Standard", required = false)
  val cellMark: String? = null,

  @Schema(description = "Local name", example = "Cell 1", required = false)
  val localName: String? = null,

  @Schema(description = "Path hierarchy", example = "MDI-A-1-001", required = true)
  val pathHierarchy: String,

  @Schema(description = "Level in the hierarchy", example = "3", required = true)
  val level: Int,

  @Schema(description = "Status of the location", example = "ACTIVE", required = true)
  val status: DerivedLocationStatus,

  @Schema(description = "Capacity of certified cell", example = "2", required = false)
  val capacityOfCertifiedCell: Int? = null,

  @Schema(description = "Working capacity", example = "2", required = false)
  val workingCapacity: Int? = null,

  @Schema(description = "Maximum capacity", example = "2", required = false)
  val maxCapacity: Int? = null,

  @Schema(description = "In-cell sanitation", example = "true", required = false)
  val inCellSanitation: Boolean? = null,

  @Schema(description = "Location type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Specialist cell types", example = "LISTENER,SAFE_CELL", required = false)
  val specialistCellTypes: List<SpecialistCellType>? = null,

  @Schema(description = "Converted cell type", example = "OFFICE", required = false)
  val convertedCellType: ConvertedCellType? = null,

  @Schema(description = "Sub-locations", required = false)
  val subLocations: List<CertificationApprovalRequestLocationDto>? = null,
) {
  companion object {
    fun from(location: CertificationApprovalRequestLocation): CertificationApprovalRequestLocationDto = CertificationApprovalRequestLocationDto(
      id = location.id!!,
      locationCode = location.locationCode,
      cellMark = location.cellMark,
      localName = location.localName,
      pathHierarchy = location.pathHierarchy,
      level = location.level,
      status = location.status,
      capacityOfCertifiedCell = location.capacityOfCertifiedCell,
      workingCapacity = location.workingCapacity,
      maxCapacity = location.maxCapacity,
      inCellSanitation = location.inCellSanitation,
      locationType = location.locationType,
      specialistCellTypes = location.getSpecialistCellTypesAsList().takeIf { it.isNotEmpty() },
      convertedCellType = location.convertedCellType,
      subLocations = location.subLocations.map { from(it) }.takeIf { it.isNotEmpty() },
    )
  }
}
