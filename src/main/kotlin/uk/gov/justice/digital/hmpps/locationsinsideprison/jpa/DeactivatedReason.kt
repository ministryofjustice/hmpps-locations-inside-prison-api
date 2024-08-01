package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Deactivated reason")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class DeactivatedReason(
  val description: String,
) {
  REFURBISHMENT("Refurbishment"),
  MAINTENANCE("Maintenance"),
  STAFF_SHORTAGE("Staff shortage"),
  MOTHBALLED("Mothballed"),
  DAMAGED("Damage"),
  DAMP("Dump / mould"),
  PEST("Pest control"),
  SECURITY_SEALED("Security sealed"),
  OTHER("Other"),
}
