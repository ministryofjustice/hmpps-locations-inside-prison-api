package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import java.time.LocalDateTime

@Schema(description = "Request to migrate a location's history")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MigrateHistoryRequest(
  @Schema(description = "Location Attribute", example = "CAPACITY", required = true)
  val attribute: LocationAttribute,

  @Schema(description = "Previous value of this attribute, null if NEW", example = "2", required = false)
  val oldValue: String? = null,

  @Schema(description = "New value of this attribute, null if REMOVED", example = "1", required = false)
  val newValue: String? = null,

  @Schema(description = "User who made the change", example = "username", required = true)
  val amendedBy: String,

  @Schema(description = "Date the change was made", example = "2023-01-23T10:15:30", required = true)
  val amendedDate: LocalDateTime,
)
