package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class LocationType(
  val description: String,
) {
  WING("Wing"),
  SPUR("Spur"),
  LANDING("Landing"),
  CELL("Cell"),
  ROOM("Room"),
  HOLDING_AREA("Holding Area"),
  MOVEMENT_AREA("Movement Area"),
  RESIDENTIAL_UNIT("Residential Unit"),
  EXTERNAL_GROUNDS("External Grounds"),
  HOLDING_CELL("Holding Cell"),
  MEDICAL("Medical"),

  GROUP("Group"),
  OFFICE("Other"),
  ADMINISTRATION_AREA("Administration Area"),
  BOOTH("Booth"),
  BOX("Box"),
  RETURN_TO_UNIT("Return to Unit"),
  CLASSROOM("Classroom"),
  TRAINING_AREA("Training Area"),
  TRAINING_ROOM("Training Room"),
  EXERCISE_AREA("Exercise Area"),
  AREA("Area"),
  SPORTS("Sports"),
  WORKSHOP("Workshop"),
  INSIDE_PARTY("Inside Party"),
  OUTSIDE_PARTY("Outside Party"),

  FAITH_AREA("Faith Area"),

  ADJUDICATION_ROOM("Adjudication Room"),
  APPOINTMENTS("Appointments"),
  VISITS("Visits"),
  VIDEO_LINK("Video Link"),
  ASSOCIATION("Association"),

  INTERNAL_GROUNDS("Internal Grounds"),
  INTERVIEW("Interview"),
  LOCATION("Location"),

  POSITION("Position"),
  SHELF("Shelf"),
  STORE("Store"),
  TABLE("Table"),
}
