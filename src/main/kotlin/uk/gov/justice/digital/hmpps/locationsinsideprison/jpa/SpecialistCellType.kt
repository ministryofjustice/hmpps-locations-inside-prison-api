package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class SpecialistCellType(
  val description: String,
) {
  BIOHAZARD_DIRTY_PROTEST("Biohazard / dirty protest cell"),
  CAT_A("Cat A cell"),
  CONSTANT_SUPERVISION("Constant supervision cell"),
  CSU("CSU cell"),
  DRY("Dry cell"),
  ESCAPE_LIST("Escape list cell"),
  FIRE_RESISTANT("Fire resistant cell"),
  FIXES_FURNITURE("Fixed furniture cell"),
  ISOLATION_DISEASES("Isolation cell for communicable diseases"),
  LIGATURE_RESISTANT("Ligature resistant cell"),
  LISTENER_CRISIS("Listener / crisis cell"),
  LOW_MOBILITY("Low mobility cell"),
  MEDICAL("Medical cell"),
  MOTHER_AND_BABY("Mother and baby cell"),
  SOUND_RESISTANT("Sound resistant cell"),
  UNFURNISHED("Unfurnished cell"),
  WHEELCHAIR_ACCESSIBLE("Wheelchair accessible cells"),
}
