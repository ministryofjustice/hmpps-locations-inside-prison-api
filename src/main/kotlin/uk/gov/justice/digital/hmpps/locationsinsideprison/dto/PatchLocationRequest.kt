package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SecurityCategoryType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.LocalDate
import java.util.*

/**
 * Request format to update a location
 */
@Schema(description = "Request to update a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatchLocationRequest(

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String? = null,

  @Schema(description = "Location Type", example = "CELL", required = true)
  override val locationType: LocationType? = null,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val localName: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  override val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,

  @Schema(description = "Capacity details of the location", required = false)
  override val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  override val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  override val attributes: Set<ResidentialAttributeValue>? = null,

  @Schema(description = "Location Usage", required = false)
  override val usage: Set<NonResidentialUsageDto>? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  override val deactivationReason: DeactivatedReason? = null,

  @Schema(description = "Proposed re-activation date", example = "2025-01-05", required = false)
  override val proposedReactivationDate: LocalDate? = null,

  @Schema(description = "Date deactivation occurred", example = "2023-01-05", required = false)
  override val deactivatedDate: LocalDate? = null,

  @Schema(description = "Accommodation Type", example = "NORMAL_ACCOMMODATION", required = false)
  val accommodationType: AccommodationType? = null,

  @Schema(description = "Specialist Cell Type", required = false)
  val specialistCellType: SpecialistCellType? = null,

  @Schema(description = "Used For Types", required = false)
  val usedFor: Set<UsedForType>? = null,

  @Schema(description = "Security Categories", required = false)
  val securityCategories: Set<SecurityCategoryType>? = null,
) : UpdateLocationRequest
