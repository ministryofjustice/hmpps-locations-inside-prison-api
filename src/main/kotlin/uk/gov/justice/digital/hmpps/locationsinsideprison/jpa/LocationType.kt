package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class ResidentialLocationType(
  val description: String,
  val baseType: LocationType,
) {
  WING("Wing", LocationType.WING),
  SPUR("Spur", LocationType.SPUR),
  LANDING("Landing", LocationType.LANDING),
  CELL("Cell", LocationType.CELL),
  ROOM("Room", LocationType.ROOM),
  AREA("Area", LocationType.LOCATION),
  HOLDING_AREA("Holding Area", LocationType.HOLDING_AREA),
  MOVEMENT_AREA("Movement Area", LocationType.MOVEMENT_AREA),
  RESIDENTIAL_UNIT("Residential Unit", LocationType.RESIDENTIAL_UNIT),
  EXTERNAL_GROUNDS("External Grounds", LocationType.EXTERNAL_GROUNDS),
  HOLDING_CELL("Holding Cell", LocationType.HOLDING_CELL),
  MEDICAL("Medical", LocationType.MEDICAL),
}

enum class NonResidentialLocationType(
  val description: String,
  val baseType: LocationType,
) {
  GROUP("Group", LocationType.GROUP),
  OFFICE("Office", LocationType.OFFICE),
  ADMINISTRATION_AREA("Administration Area", LocationType.ADMINISTRATION_AREA),
  BOOTH("Booth", LocationType.BOOTH),
  BOX("Box", LocationType.BOX),
  RETURN_TO_UNIT("Return to Unit", LocationType.RETURN_TO_UNIT),
  CLASSROOM("Classroom", LocationType.CLASSROOM),
  TRAINING_AREA("Training Area", LocationType.TRAINING_AREA),
  TRAINING_ROOM("Training Room", LocationType.TRAINING_ROOM),
  EXERCISE_AREA("Exercise Area", LocationType.EXERCISE_AREA),

  SPORTS("Sports", LocationType.SPORTS),
  WORKSHOP("Workshop", LocationType.WORKSHOP),
  INSIDE_PARTY("Inside Party", LocationType.INSIDE_PARTY),
  OUTSIDE_PARTY("Outside Party", LocationType.OUTSIDE_PARTY),

  FAITH_AREA("Faith Area", LocationType.FAITH_AREA),

  ADJUDICATION_ROOM("Adjudication Room", LocationType.ADJUDICATION_ROOM),
  APPOINTMENTS("Appointments", LocationType.APPOINTMENTS),
  VISITS("Visits", LocationType.VISITS),
  VIDEO_LINK("Video Link", LocationType.VIDEO_LINK),
  ASSOCIATION("Association", LocationType.ASSOCIATION),

  INTERNAL_GROUNDS("Internal Grounds", LocationType.INTERNAL_GROUNDS),
  INTERVIEW("Interview", LocationType.INTERVIEW),
  LOCATION("Location", LocationType.LOCATION),

  POSITION("Position", LocationType.POSITION),
  SHELF("Shelf", LocationType.SHELF),
  STORE("Store", LocationType.STORE),
  TABLE("Table", LocationType.TABLE),
}

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
  OFFICE("Office"),
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
