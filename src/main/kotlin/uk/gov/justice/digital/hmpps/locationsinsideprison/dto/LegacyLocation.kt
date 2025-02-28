package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Schema(description = "Location Information - for NOMIS sync")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LegacyLocation(
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

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  val comments: String? = null,

  @Schema(description = "When set to true DO NOT SYNC the working capacity", required = true, defaultValue = "false")
  val ignoreWorkingCapacity: Boolean = false,

  @Schema(description = "Capacity details of the location", required = false)
  val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  val attributes: List<ResidentialAttributeValue>? = null,

  @Schema(description = "Location Usage", required = false)
  val usage: List<NonResidentialUsageDto>? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  val orderWithinParentLocation: Int? = null,

  @Schema(description = "Indicates the location is enabled", example = "true", required = true)
  val active: Boolean = true,

  @Schema(description = "Date the location was deactivated", example = "2023-01-23", required = false)
  val deactivatedDate: LocalDate? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @Schema(description = "Estimated reactivation date for location reactivation", example = "2026-01-24", required = false)
  val proposedReactivationDate: LocalDate? = null,

  @Schema(description = "Indicates that this location has been permanently deactivated and should not be changed in NOMIS", example = "false", defaultValue = "false", required = true)
  val permanentlyDeactivated: Boolean = false,

  @Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @Schema(description = "History of changes", required = false)
  val changeHistory: List<ChangeHistory>? = null,

  @Schema(description = "Staff username who last changed the location", required = true)
  val lastModifiedBy: String,

  @Schema(description = "Date and time of the last change", required = true)
  val lastModifiedDate: LocalDateTime,
) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"
}
