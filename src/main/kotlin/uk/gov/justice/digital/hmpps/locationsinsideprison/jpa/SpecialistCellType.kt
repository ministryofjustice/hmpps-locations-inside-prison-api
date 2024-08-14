package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Specialist Cell Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class SpecialistCellType(
  val description: String,
  val additionalInformation: String? = null,
) {
  ACCESSIBLE_CELL("Accessible cell", "Also known as wheelchair accessible or Disability and Discrimination Act (DDA) compliant"),
  BIOHAZARD_DIRTY_PROTEST("Biohazard / dirty protest cell", "Previously known as a dirty protest cell"),
  CONSTANT_SUPERVISION("Constant supervision cell"),
  CAT_A("Cat A cell"),
  CSU("Care and separation cell"),
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
