package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class AccommodationType(
  val description: String,
) {
  NORMAL_ACCOMMODATION("Normal Accommodation"),
  HEALTHCARE_INPATIENTS("Health Care In-patients"),
  CARE_AND_SEPARATION("Care and Separation"),
}
