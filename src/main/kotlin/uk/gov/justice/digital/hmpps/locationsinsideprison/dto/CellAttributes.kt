package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Cell attribute Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellAttributes(
  @Schema(description = "Attribute Code", example = "CAT_A", required = true)
  val code: Any,

  @Schema(description = "Attribute description", example = "Cat A cell", required = true)
  val description: String,
)
