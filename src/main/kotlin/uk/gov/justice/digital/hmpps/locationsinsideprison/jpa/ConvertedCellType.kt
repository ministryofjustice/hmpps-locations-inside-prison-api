package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Converted Cell Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class ConvertedCellType(
  val description: String,
) {
  OFFICE("Office"),
  SHOWER("Shower"),
  STORE("Store room"),
  UTILITY_ROOM("Utility room"),
  HOLDING_ROOM("Holding room"),
  INTERVIEW_ROOM("Interview room"),
  KITCHEN_SERVERY("Kitchen / Servery"),
  TREATMENT_ROOM("Treatment room"),
  STAFF_ROOM("Staff room"),
  OTHER("Other"),
}
