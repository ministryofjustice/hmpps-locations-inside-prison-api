package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Specialist Cell Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class SpecialistCellType(
  val description: String,
  val additionalInformation: String? = null,
  val sequence: Int = 99,
) {
  ACCESSIBLE_CELL("Accessible cell", "Also known as wheelchair accessible or Disability and Discrimination Act (DDA) compliant", 1),
  BIOHAZARD_DIRTY_PROTEST("Biohazard / dirty protest cell", "Previously known as a dirty protest cell", 2),
  CSU("Care and separation cell", sequence = 3),
  CAT_A("Cat A cell", sequence = 4),
  CONSTANT_SUPERVISION("Constant supervision cell", sequence = 5),
  DRY("Dry cell", sequence = 6),
  ESCAPE_LIST("Escape list cell", sequence = 7),
  ISOLATION_DISEASES("Isolation cell for communicable diseases", sequence = 8),
  LISTENER_CRISIS("Listener / crisis cell", sequence = 9),
  LOCATE_FLAT_CELL("Locate flat", sequence = 10),
  MEDICAL("Medical cell", sequence = 11),
  MOTHER_AND_BABY("Mother and baby cell", sequence = 12),
  SAFE_CELL("Safe cell", sequence = 13),
  UNFURNISHED("Unfurnished cell", sequence = 14),
}
