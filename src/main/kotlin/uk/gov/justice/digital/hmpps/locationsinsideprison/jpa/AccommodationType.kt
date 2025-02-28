package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType.HEALTHCARE
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType.OTHER_USE
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType.SEGREGATION

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
  ;

  fun mapToResidentialHousingType(): ResidentialHousingType = when (this) {
    NORMAL_ACCOMMODATION -> ResidentialHousingType.NORMAL_ACCOMMODATION
    HEALTHCARE_INPATIENTS -> HEALTHCARE
    CARE_AND_SEPARATION -> SEGREGATION
    OTHER_NON_RESIDENTIAL -> OTHER_USE
  }
}
