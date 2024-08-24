package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.ALWAYS)
class LocationGroupDtoListTest(

  val locationGroupDto: List<LocationGroupDto>? = null,

)
