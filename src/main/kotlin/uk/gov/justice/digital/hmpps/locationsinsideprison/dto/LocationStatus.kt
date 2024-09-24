package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

enum class LocationStatus(
  val description: String,
) {
  ACTIVE("Active"),
  INACTIVE("Inactive"),
  NON_RESIDENTIAL("Non-residential"),
  ARCHIVED("Archived"),
}
