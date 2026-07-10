package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.util.UUID

@Schema(description = "A leaf location that can hold prisoner property, with the capacity of its PROPERTY usage")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PropertyLocationDto(
  @param:Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Location Code", example = "PROP1", required = true)
  val code: String,

  @param:Schema(description = "Full path of the location within the prison", example = "PROP-1-001", required = true)
  val pathHierarchy: String,

  @param:Schema(description = "Description to display for the location", example = "Property store 1", required = false)
  val localName: String? = null,

  @param:Schema(description = "Location Type", example = "BOX", required = true)
  val locationType: LocationType,

  @param:Schema(
    description = "Capacity of this location's PROPERTY usage - how many property containers it can hold. May be null if not configured.",
    example = "10",
    required = false,
  )
  val capacity: Int? = null,
)
