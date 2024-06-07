package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import io.swagger.v3.oas.annotations.media.Schema

data class LocationGroupDto(

  @Schema(description = "Group name", example = "Block A", required = true)
  val name: String?,

  @Schema(description = "Group key", example = "A", required = true)
  val key: String,

  @Schema(description = "The child groups of this group", example = "[{\"name\": \"Landing A/1\", \"key\":\"1\"}, {\"name\": \"Landing A/2\", \"key\": \"2\"}]", required = true)
  val children: List<LocationGroupDto>? = null,
)
