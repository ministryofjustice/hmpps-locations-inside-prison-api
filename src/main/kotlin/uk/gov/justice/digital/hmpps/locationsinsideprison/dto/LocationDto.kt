package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

@Schema(description = "Location Information")
data class Location(
  @Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Location Code", example = "001", required = true)
  val code: String,

  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @Schema(description = "Top Level Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = true)
  val topLevelId: UUID,

) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }
}

/**
 * Request format to create a location
 */
@Schema(description = "Request to create a location")
data class CreateLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3)
  @field:Size(min = 3, message = "PrisonId cannot be blank")
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,
) {

  fun toNewEntity(createdBy: String, clock: Clock): LocationJPA {
    return LocationJPA(
      id = null,
      prisonId = prisonId,
      code = code,
      locationType = locationType,
      pathHierarchy = code,
      updatedBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      whenUpdated = LocalDateTime.now(clock),
    )
  }
}

/**
 * Request format to create a location
 */
@Schema(description = "Request to update a location")
data class UpdateLocationRequest(

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,
)
