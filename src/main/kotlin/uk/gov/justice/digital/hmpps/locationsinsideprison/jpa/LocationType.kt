package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

enum class ResidentialLocationType(
  val description: String,
  val baseType: LocationType,
  val display: Boolean = true,
) {
  BLOCK("Block", LocationType.BLOCK),
  WING("Wing", LocationType.WING),
  SPUR("Spur", LocationType.SPUR),
  LANDING("Landing", LocationType.LANDING),
  CELL("Cell", LocationType.CELL),
  ROOM("Room", LocationType.ROOM),
  AREA("Area", LocationType.AREA, false),
  HOLDING_AREA("Holding Area", LocationType.HOLDING_AREA, false),
  MOVEMENT_AREA("Movement Area", LocationType.MOVEMENT_AREA, false),
  RESIDENTIAL_UNIT("Residential Unit", LocationType.RESIDENTIAL_UNIT, false),
  EXTERNAL_GROUNDS("External Grounds", LocationType.EXTERNAL_GROUNDS, false),
  HOLDING_CELL("Holding Cell", LocationType.HOLDING_CELL, false),
  MEDICAL("Medical", LocationType.MEDICAL, false),
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
  BLOCK("Block"),
  WING("Wing"),
  SPUR("Spur"),
  LANDING("Landing"),
  CELL("Cell"),
  ROOM("Room"),
  HOLDING_AREA("Holding area"),
  MOVEMENT_AREA("Movement area"),
  RESIDENTIAL_UNIT("Residential unit"),
  EXTERNAL_GROUNDS("External grounds"),
  HOLDING_CELL("Holding cell"),
  MEDICAL("Medical"),

  GROUP("Group"),
  OFFICE("Office"),
  ADMINISTRATION_AREA("Administration area"),
  BOOTH("Booth"),
  BOX("Box"),
  RETURN_TO_UNIT("Return to unit"),
  CLASSROOM("Classroom"),
  TRAINING_AREA("Training area"),
  TRAINING_ROOM("Training room"),
  EXERCISE_AREA("Exercise area"),
  AREA("Area"),
  SPORTS("Sports"),
  WORKSHOP("Workshop"),
  INSIDE_PARTY("Inside party"),
  OUTSIDE_PARTY("Outside party"),

  FAITH_AREA("Faith area"),

  ADJUDICATION_ROOM("Adjudication room"),
  APPOINTMENTS("Appointments"),
  VISITS("Visits"),
  VIDEO_LINK("Video link"),
  ASSOCIATION("Association"),

  INTERNAL_GROUNDS("Internal grounds"),
  INTERVIEW("Interview"),
  LOCATION("Location"),

  POSITION("Position"),
  SHELF("Shelf"),
  STORE("Store"),
  TABLE("Table"),
}
