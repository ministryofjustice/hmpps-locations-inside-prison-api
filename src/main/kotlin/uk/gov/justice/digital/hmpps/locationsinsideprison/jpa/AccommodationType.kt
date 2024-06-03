package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class AccommodationType(
  val description: String,
) {
  NORMAL_ACCOMMODATION("Normal accommodation"),
  HEALTHCARE_INPATIENTS("Healthcare inpatients"),
  CARE_AND_SEPARATION("Care and separation"),
  OTHER_NON_RESIDENTIAL("Other"),
}
