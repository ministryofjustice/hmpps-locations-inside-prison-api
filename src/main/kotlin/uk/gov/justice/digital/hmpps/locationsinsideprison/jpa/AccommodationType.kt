package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Accommodation Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class AccommodationType(
  val description: String,
) {
  NORMAL_ACCOMMODATION("Normal accommodation"),
  HEALTHCARE_INPATIENTS("Healthcare inpatients"),
  CARE_AND_SEPARATION("Care and separation"),
  OTHER_NON_RESIDENTIAL("Other"),
}
