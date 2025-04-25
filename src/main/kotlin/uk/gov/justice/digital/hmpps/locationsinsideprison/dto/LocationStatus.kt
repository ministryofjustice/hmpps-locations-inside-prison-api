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

  LOCKED_ACTIVE("Active (Locked)"),
  LOCKED_INACTIVE("Inactive (Locked)"),
  LOCKED_DRAFT("Draft (Locked)"),
  LOCKED_NON_RESIDENTIAL("Non-residential (Locked)"),
}
