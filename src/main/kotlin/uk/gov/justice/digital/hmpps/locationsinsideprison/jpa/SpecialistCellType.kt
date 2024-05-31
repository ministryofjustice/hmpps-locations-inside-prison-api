package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class SpecialistCellType(
  val description: String,
) {
  ACCESSIBLE_CELL("Accessible cell"),
  BIOHAZARD_DIRTY_PROTEST("Biohazard / dirty protest cell"),
  CONSTANT_SUPERVISION("Constant Supervision Cell"),
  CAT_A("Cat A cell"),
  CSU("Case and separation unit cell"),
  DRY("Dry cell"),
  ESCAPE_LIST("Escape list cell"),
  ISOLATION_DISEASES("Isolation cell for communicable diseases"),
  LISTENER_CRISIS("Listener / crisis cell"),
  LOCATE_FLAT_CELL("Locate flat"),
  MEDICAL("Medical cell"),
  MOTHER_AND_BABY("Mother and baby cell"),
  SAFE_CELL("Safe cell"),
  UNFURNISHED("Unfurnished cell"),
}
