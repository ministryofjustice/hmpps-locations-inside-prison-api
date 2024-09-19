package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Deactivated reason")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class DeactivatedReason(
  val description: String,
  val sequence: Int = 99,
  val outOfUse: Boolean = true,
) {
  DAMAGED("Damage", 1),
  DAMP("Damp / mould", 2),
  MAINTENANCE("Maintenance", 3),
  MOTHBALLED("Mothballed", 4, outOfUse = false),
  PEST("Pest control", 5),
  REFURBISHMENT("Refurbishment", 6),
  SECURITY_SEALED("Security sealed", 7),
  STAFF_SHORTAGE("Staff shortage", 8),
  OTHER("Other", 99),
}
