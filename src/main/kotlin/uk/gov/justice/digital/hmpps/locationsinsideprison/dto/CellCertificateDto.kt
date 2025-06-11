package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Cell Certificate")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCertificateDto(
  @Schema(description = "ID of the cell certificate", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Who approved the certificate", example = "USER1", required = true)
  val approvedBy: String,

  @Schema(description = "When the certificate was approved", example = "2023-01-01T12:00:00", required = true)
  val approvedDate: LocalDateTime,

  @Schema(description = "ID of the certification approval request", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val certificationApprovalRequestId: UUID,

  @Schema(description = "Total working capacity for the prison", example = "100", required = true)
  val totalWorkingCapacity: Int,

  @Schema(description = "Total max capacity for the prison", example = "120", required = true)
  val totalMaxCapacity: Int,

  @Schema(description = "Total capacity of certified cells for the prison", example = "110", required = true)
  val totalCertifiedNormalAccommodation: Int,

  @Schema(description = "Whether this is the current certificate", example = "true", required = true)
  val current: Boolean,

  @Schema(description = "The approval request that created the certificate", required = true)
  val approvedRequest: CertificationApprovalRequestDto,

  @Schema(description = "Locations in the certificate", required = true)
  val locations: List<CellCertificateLocationDto>? = null,
)

@Schema(description = "Cell Certificate Location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCertificateLocationDto(

  @Schema(description = "Location code", example = "001", required = true)
  val locationCode: String,

  @Schema(description = "Path hierarchy", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @Schema(description = "Capacity of certified cell", example = "2")
  val certifiedNormalAccommodation: Int?,

  @Schema(description = "Working capacity", example = "1")
  val workingCapacity: Int?,

  @Schema(description = "Max capacity", example = "2")
  val maxCapacity: Int?,

  @Schema(description = "Whether the cell has in-cell sanitation", example = "true")
  val inCellSanitation: Boolean?,

  @Schema(description = "Location type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Specialist cell types")
  val specialistCellTypes: List<SpecialistCellType>,

  @Schema(description = "Local name for the location, not used in cells", example = "Houseblock A")
  val localName: String? = null,

  @Schema(description = "Cell mark", example = "T-01")
  val cellMark: String? = null,

  @Schema(description = "Level within the hierarchy", example = "3", required = true)
  val level: Int,

  @Schema(description = "If converted, the type of cell this location has been converted to")
  val convertedCellType: ConvertedCellType? = null,

  @Schema(description = "Sub locations within this cell certificate location")
  val subLocations: List<CellCertificateLocationDto>? = null,
)
