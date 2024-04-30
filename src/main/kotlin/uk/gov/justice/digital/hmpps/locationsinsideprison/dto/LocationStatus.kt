package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

enum class LocationStatus(
  val description: String,
) {
  ACTIVE("Active"),
  INACTIVE("In-Active"),
  NON_RESIDENTIAL("Non-Residential"),
}
