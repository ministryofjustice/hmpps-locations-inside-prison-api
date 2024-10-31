package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Converted Cell Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class ConvertedCellType(
  val description: String,
  val sequence: Int = 99,
) {
  HOLDING_ROOM("Holding room", 1),
  INTERVIEW_ROOM("Interview room", 2),
  KITCHEN_SERVERY("Kitchen / Servery", 3),
  LISTENERS_ROOM("Listener's room", 4),
  OFFICE("Office", 5),
  SHOWER("Shower", 6),
  STAFF_ROOM("Staff room", 7),
  STORE("Store room", 8),
  TREATMENT_ROOM("Treatment room", 9),
  UTILITY_ROOM("Utility room", 10),
  OTHER("Other", 99),
}
