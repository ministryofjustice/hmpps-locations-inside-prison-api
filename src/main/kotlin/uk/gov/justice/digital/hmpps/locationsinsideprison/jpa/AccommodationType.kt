package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Accommodation Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class AccommodationType(
  val description: String,
  val sequence: Int = 99,
) {
  CARE_AND_SEPARATION("Care and separation", 1),
  HEALTHCARE_INPATIENTS("Healthcare inpatients", 2),
  NORMAL_ACCOMMODATION("Normal accommodation", 3),
  OTHER_NON_RESIDENTIAL("Other", 99),
}
