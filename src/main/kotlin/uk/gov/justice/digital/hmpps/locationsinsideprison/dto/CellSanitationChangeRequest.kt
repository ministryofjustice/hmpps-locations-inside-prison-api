package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request change of cell sanitation and add the reason for change is certification is required")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellSanitationChangeRequest(
  @param:Schema(description = "Whether the cell has in-cell sanitation", example = "true")
  val inCellSanitation: Boolean,
  @param:Schema(description = "The reason why the approval was requested, mandatory if the cell change must be approved", example = "The toilet is broken", required = false)
  val reasonForChange: String? = null,
)
