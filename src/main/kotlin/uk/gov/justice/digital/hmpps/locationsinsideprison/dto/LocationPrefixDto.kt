package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import io.swagger.v3.oas.annotations.media.Schema

data class LocationPrefixDto(
  @param:Schema(description = "Location prefix translated from group name", example = "MDI-1-")
  val locationPrefix: String,
)
