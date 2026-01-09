package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Specialist Cell Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class SpecialistCellType(
  val description: String,
  val affectsCapacity: Boolean = false,
  val additionalInformation: String? = null,
  val sequence: Int = 99,
) {
  ACCESSIBLE_CELL("Accessible cell", additionalInformation = "Also known as wheelchair accessible or Disability and Discrimination Act (DDA) compliant", sequence = 1),
  BIOHAZARD_DIRTY_PROTEST(
    "Biohazard cell",
    additionalInformation = "Previously known as a dirty protest cell",
    sequence = 2,
    affectsCapacity = true,
  ),
  CSU("Care and separation cell", sequence = 3, affectsCapacity = true),
  CAT_A("Cat A cell", sequence = 4),
  CONSTANT_SUPERVISION("Constant supervision cell", sequence = 5, affectsCapacity = true),
  DRY("Dry cell", sequence = 6, affectsCapacity = true),
  ESCAPE_LIST("Escape list cell", sequence = 7),
  ISOLATION_DISEASES("Isolation cell for communicable diseases", sequence = 8),
  LISTENER_CRISIS("Listener / crisis cell", sequence = 9),
  LOCATE_FLAT_CELL("Locate flat", sequence = 10),
  MEDICAL("Medical cell", sequence = 11),
  MOTHER_AND_BABY("Mother and baby cell", sequence = 12),
  SAFE_CELL("Safe cell", sequence = 13),
  UNFURNISHED("Unfurnished cell", sequence = 14, affectsCapacity = true),
}
