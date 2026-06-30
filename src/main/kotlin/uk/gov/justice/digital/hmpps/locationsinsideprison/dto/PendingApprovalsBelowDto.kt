package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.util.UUID

@Schema(description = "Pending certification approval requests on locations below a given location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PendingApprovalsBelowDto(
  @param:Schema(description = "True if any location below the requested location has a pending certification approval request", example = "true", required = true)
  val hasPendingBelow: Boolean,

  @param:Schema(description = "The locations below the requested location that have a pending certification approval request", required = true)
  val pendingLocations: List<PendingApprovalLocationDto>,
)

@Schema(description = "A location with a pending certification approval request, including its parent")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PendingApprovalLocationDto(
  @param:Schema(description = "The id of the location with the pending approval request", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,
  @param:Schema(description = "The business key of the location with the pending approval request", example = "LEI-B-1-001", required = true)
  val key: String,
  @param:Schema(description = "The local name of the location with the pending approval request, if any", example = "B-1-001", required = false)
  val localName: String?,
  @param:Schema(description = "The type of the location with the pending approval request", example = "CELL", required = true)
  val locationType: LocationType,

  @param:Schema(description = "The id of the parent of the location with the pending approval request", example = "0199e835-9eb8-7183-ab7e-f79149e5c1f8", required = false)
  val parentId: UUID?,
  @param:Schema(description = "The business key of the parent location", example = "LEI-B-1", required = false)
  val parentKey: String?,
  @param:Schema(description = "The local name of the parent location, if any", example = "Landing B-1", required = false)
  val parentLocalName: String?,
  @param:Schema(description = "The type of the parent location", example = "LANDING", required = false)
  val parentLocationType: LocationType?,
)
