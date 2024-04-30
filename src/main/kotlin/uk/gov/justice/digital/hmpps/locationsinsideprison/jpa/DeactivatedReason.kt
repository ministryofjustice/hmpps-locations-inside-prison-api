package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class DeactivatedReason(
  val description: String,
) {
  REFURBISHMENT("Refurbishment"),
  LOCAL_WORK("Maintenance"),
  STAFF_SHORTAGE("Staff Shortage"),
  MOTHBALLED("Mothballed"),
  DAMAGED("Damaged"),
  NEW_BUILDING("New Building"),
  CELL_RECLAIMS("Cell Reclaims"),
  CHANGE_OF_USE("Change of Use"),
  CLOSURE("Closure"),
  OUT_OF_USE("Out of Use"),
  CELLS_RETURNING_TO_USE("Cells Returning to Use"),
  OTHER("Other"),
  ;

  fun mapsTo(): DeactivatedReason = when (this) {
    REFURBISHMENT -> REFURBISHMENT
    LOCAL_WORK -> LOCAL_WORK
    STAFF_SHORTAGE -> STAFF_SHORTAGE
    MOTHBALLED -> MOTHBALLED
    DAMAGED -> DAMAGED
    else -> OTHER
  }

  fun isPermanentlyInactiveReason() = when (this) {
    CLOSURE, MOTHBALLED, CHANGE_OF_USE, NEW_BUILDING -> true
    else -> false
  }
}
