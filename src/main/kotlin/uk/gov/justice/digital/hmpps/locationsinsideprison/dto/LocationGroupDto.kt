package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SortAttribute

data class LocationGroupDto(

  @Schema(description = "Group name", example = "Block A", required = true)
  val name: String?,

  @Schema(description = "Group key", example = "A", required = true)
  val key: String,

  @Schema(description = "The child groups of this group", example = "[{\"name\": \"Landing A/1\", \"key\":\"1\"}, {\"name\": \"Landing A/2\", \"key\": \"2\"}]", required = true)
  val children: List<LocationGroupDto>? = null,
) : SortAttribute {

  @JsonIgnore
  override fun getSortName(): String {
    return name ?: key
  }
}
