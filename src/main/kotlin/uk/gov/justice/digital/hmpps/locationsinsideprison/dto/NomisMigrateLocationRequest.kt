package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

/**
 * Request format to migrate a location and it's history
 */
@Schema(description = "Request to migrate a location and it's history")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NomisMigrateLocationRequest(

  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  override val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String,

  @Schema(description = "Location Type", example = "CELL", required = true)
  override val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Local name must be less than 81 characters")
  override val localName: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  override val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  override val deactivationReason: NomisDeactivatedReason? = null,

  @Schema(description = "Estimated reactivation date", example = "2025-01-05", required = false)
  override val proposedReactivationDate: LocalDate? = null,

  @Schema(description = "Date deactivation occurred", example = "2023-01-05", required = false)
  override val deactivatedDate: LocalDate? = null,

  @Schema(description = "Path hierarchy of the parent (if one exists)", example = "A-1", required = false)
  override val parentLocationPath: String? = null,

  @Schema(description = "Parent UUID of the parent location (if one exists)", example = "2475f250-434a-4257-afe7-b911f1773a4e", required = false)
  override val parentId: UUID? = null,

  @Schema(description = "Capacity details of the location", required = false)
  override val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  override val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  override val attributes: Set<ResidentialAttributeValue>? = null,

  @Schema(description = "Location Usage", required = false)
  override val usage: Set<NonResidentialUsageDto>? = null,

  @Schema(description = "Date location was created, if not provided then the current time will be used for a new location", required = false)
  override val createDate: LocalDateTime? = null,

  @Schema(description = "Last updated, if not provided then the current time will be used", required = false)
  override val lastModifiedDate: LocalDateTime? = null,

  @Schema(description = "Username of the staff updating the location", required = true)
  override val lastUpdatedBy: String,

  @Schema(description = "History of changes to the location", required = false)
  val history: List<ChangeHistory>? = null,

) : NomisMigrationRequest {

  override fun toNewEntity(clock: Clock, linkedTransaction: LinkedTransaction): LocationJPA {
    val location = createLocation(clock, linkedTransaction)
    history?.map {
      location.addHistory(
        attributeName = LocationAttribute.valueOf(it.attribute),
        oldValue = it.oldValue,
        newValue = it.newValue,
        amendedBy = it.amendedBy,
        amendedDate = it.amendedDate,
        linkedTransaction = linkedTransaction,
      )
    }
    return location
  }
}
