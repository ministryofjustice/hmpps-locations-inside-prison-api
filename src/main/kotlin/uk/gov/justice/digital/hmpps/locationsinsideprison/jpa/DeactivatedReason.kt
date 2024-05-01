package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

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
