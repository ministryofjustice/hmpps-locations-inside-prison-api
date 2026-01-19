package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "Request change of cell mark and add the reason for change is certification is required")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellMarkChangeRequest(
  @param:Schema(description = "Cell mark of the location", required = true, example = "A1", minLength = 1)
  @field:Size(min = 1, message = "Mark cannot be blank")
  @field:Size(max = 12, message = "Mark can be up to 12 characters")
  val cellMark: String,
  @param:Schema(description = "The reason why the approval was requested, mandatory if the cell change must be approved", example = "The door mark has been updated", required = false)
  val reasonForChange: String? = null,
)
