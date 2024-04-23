package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class AccommodationType(
  val description: String,
) {
  NORMAL_ACCOMMODATION("Normal Accommodation"),
  SPECIAL_ACCOMMODATION("Specialist Accommodation"),
}
