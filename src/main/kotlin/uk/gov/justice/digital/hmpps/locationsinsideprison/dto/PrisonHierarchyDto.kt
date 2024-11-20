package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SortAttribute
import java.util.*

@Schema(description = "Prison hierarchy")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonHierarchyDto(

  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Location Code", example = "001", required = true)
  val locationCode: String,

  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val fullLocationPath: String,

  @Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @Schema(
    description = "Current Level within hierarchy, starts at 1, e.g Wing = 1",
    examples = ["1", "2", "3"],
    required = true,
  )
  val level: Int,

  @Schema(description = "Sub residential locations", required = false)
  val subLocations: List<PrisonHierarchyDto>? = null,
) : SortAttribute {

  @JsonIgnore
  override fun getSortName(): String {
    return localName?.capitalizeWords() ?: fullLocationPath
  }
}
