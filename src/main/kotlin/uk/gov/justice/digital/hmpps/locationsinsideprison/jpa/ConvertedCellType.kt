package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class ConvertedCellType(
  val description: String,
) {
  OFFICE("Office"),
  SHOWER("Shower"),
  STORE("Store room"),
  UTILITY_ROOM("Utility room"),
  HOLDING_ROOM("Holding room"),
  INTERVIEW_ROOM("Interview Room"),
  KITCHEN_SERVERY("Kitchen / Servery"),
  TREATMENT_ROOM("Treatment room"),
  STAFF_ROOM("Staff room"),
  OTHER("Other"),
}
