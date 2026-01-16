package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

enum class LocationStatus(
  val description: String,
) {
  ACTIVE("Active"),
  INACTIVE("Inactive"),
  ARCHIVED("Archived"),
  DRAFT("Draft"),
}

enum class DerivedLocationStatus(
  val description: String,
) {
  ACTIVE("Active"),
  INACTIVE("Inactive"),
  ARCHIVED("Archived"),
  DRAFT("Draft"),

  NON_RESIDENTIAL("Non-residential"),

  LOCKED_ACTIVE("Active (Change requested)"),
  LOCKED_INACTIVE("Inactive (Change requested)"),
  LOCKED_DRAFT("Draft (Change requested)"),
  LOCKED_NON_RESIDENTIAL("Non-residential (Change requested)"),
}
