package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.util.UUID

@Schema(description = "Location affected by certification approval")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CertificationApprovalRequestLocationDto(
  @param:Schema(description = "Location ID", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @param:Schema(description = "Location code", example = "MDI-A-1-001", required = true)
  val locationCode: String,

  @param:Schema(description = "Cell mark", example = "Standard", required = false)
  val cellMark: String? = null,

  @param:Schema(description = "Local name", example = "Cell 1", required = false)
  val localName: String? = null,

  @param:Schema(description = "Path hierarchy", example = "MDI-A-1-001", required = true)
  val pathHierarchy: String,

  @param:Schema(description = "Level in the hierarchy", example = "3", required = true)
  val level: Int,

  @param:Schema(description = "Capacity of certified cell", example = "2", required = false)
  val certifiedNormalAccommodation: Int? = null,

  @param:Schema(description = "Working capacity", example = "2", required = false)
  val workingCapacity: Int? = null,

  @param:Schema(description = "Maximum capacity", example = "2", required = false)
  val maxCapacity: Int? = null,

  @param:Schema(description = "In-cell sanitation", example = "true", required = false)
  val inCellSanitation: Boolean? = null,

  @param:Schema(description = "Location type", example = "CELL", required = true)
  val locationType: LocationType,

  @param:Schema(description = "Accommodation Types", required = false)
  val accommodationTypes: List<AccommodationType>? = null,

  @param:Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: List<SpecialistCellType>? = null,

  @param:Schema(description = "Usage For", required = false)
  val usedFor: List<UsedForType>? = null,

  @param:Schema(description = "Converted cell type", example = "OFFICE", required = false)
  val convertedCellType: ConvertedCellType? = null,

  @param:Schema(description = "Sub-locations", required = false)
  val subLocations: List<CertificationApprovalRequestLocationDto>? = null,
)
