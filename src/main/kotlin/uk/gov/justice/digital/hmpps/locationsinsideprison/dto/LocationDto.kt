package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity as CapacityJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell as CellJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification as CertificationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation as NonResidentialLocationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation as ResidentialLocationJPA

@Schema(description = "Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
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

  @Schema(description = "If residential location, its type", example = "NORMAL_ACCOMMODATION", required = false)
  val residentialHousingType: ResidentialHousingType? = null,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  val comments: String? = null,

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

  @Schema(description = "Date the location was reactivated", example = "2023-01-24", required = false)
  val reactivatedDate: LocalDate? = null,

  @Schema(description = "Top Level Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = true)
  val topLevelId: UUID,

  @Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @Schema(description = "Parent Location", required = false)
  val parentLocation: Location? = null,

  @Schema(description = "Child Locations", required = false)
  val childLocations: List<Location>? = null,

  @Schema(description = "History of changes", required = false)
  val changeHistory: List<ChangeHistory>? = null,
) {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }

  @Schema(description = "Indicates if the location is a residential location", example = "true", required = true)
  fun isResidential(): Boolean {
    return residentialHousingType != null
  }

  @JsonIgnore
  fun getLocationAndSubLocations(): List<Location> {
    val locations = mutableListOf<Location>()

    fun traverse(location: Location) {
      locations.add(location)
      location.childLocations?.forEach { childLocation ->
        traverse(childLocation)
      }
    }

    traverse(this)
    return locations
  }
}

@Schema(description = "Non Residential Usage")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NonResidentialUsageDto(
  val usageType: NonResidentialUsageType,
  val capacity: Int? = null,
  val sequence: Int = 99,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NonResidentialUsageDto

    if (usageType != other.usageType) return false
    if (capacity != other.capacity) return false
    if (sequence != other.sequence) return false

    return true
  }

  override fun hashCode(): Int {
    var result = usageType.hashCode()
    result = 31 * result + (capacity ?: 0)
    result = 31 * result + sequence
    return result
  }
}

@Schema(description = "Capacity")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Capacity(
  @Schema(description = "Capacity of the location", example = "2", required = false)
  val capacity: Int = 0,
  @Schema(description = "Operational capacity of the location", example = "2", required = false)
  val operationalCapacity: Int = 0,
) {
  fun toNewEntity(): CapacityJPA {
    return CapacityJPA(capacity = capacity, operationalCapacity = operationalCapacity)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Capacity

    if (capacity != other.capacity) return false
    if (operationalCapacity != other.operationalCapacity) return false

    return true
  }

  override fun hashCode(): Int {
    var result = capacity
    result = 31 * result + operationalCapacity
    return result
  }
}

@Schema(description = "Change History")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChangeHistory(
  @Schema(description = "Location Attribute", example = "Location Type", required = true)
  val attribute: String,

  @Schema(description = "Previous value of this attribute", example = "CELL", required = false)
  val oldValue: String? = null,

  @Schema(description = "New value of this attribute", example = "WING", required = false)
  val newValue: String? = null,

  @Schema(description = "User who made the change", example = "user", required = true)
  val amendedBy: String,

  @Schema(description = "Date the change was made", example = "2023-01-23T10:15:30", required = true)
  val amendedDate: LocalDateTime,
)

@Schema(description = "Certification")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Certification(
  @Schema(description = "Indicates that this location is certified for use as a residential location", example = "true", required = false)
  val certified: Boolean = false,
  @Schema(description = "Indicates the capacity of the certified location (cell)", example = "1", required = false)
  val capacityOfCertifiedCell: Int = 0,
) {
  fun toNewEntity(): CertificationJPA {
    return CertificationJPA(certified = certified, capacityOfCertifiedCell = capacityOfCertifiedCell)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Certification

    if (certified != other.certified) return false
    if (capacityOfCertifiedCell != other.capacityOfCertifiedCell) return false

    return true
  }

  override fun hashCode(): Int {
    var result = certified.hashCode()
    result = 31 * result + capacityOfCertifiedCell
    return result
  }
}

interface CreateRequest {
  val prisonId: String
  val code: String
  val locationType: LocationType
  val description: String?
  val comments: String?
  val orderWithinParentLocation: Int?
  val parentId: UUID?
  fun toNewEntity(createdBy: String, clock: Clock): LocationJPA
  fun isCell(): Boolean = locationType == LocationType.CELL
}

/**
 * Request format to create a residential location
 */
@Schema(description = "Request to create a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateResidentialLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 3)
  @field:Size(min = 3, message = "PrisonId cannot be blank")
  @field:Size(max = 3, message = "PrisonId must be 3 characters")
  override val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be up to 12 characters")
  override val code: String,

  @Schema(description = "residential location type", example = "NORMAL_ACCOMMODATION", required = true)
  val residentialHousingType: ResidentialHousingType,

  @Schema(description = "Location Type", example = "CELL", required = true)
  override val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @Schema(description = "Capacity of the residential location", required = false)
  val capacity: Capacity? = null,

  @Schema(description = "Certified status of the residential location", required = false)
  val certification: Certification? = null,

  @Schema(description = "Location Attributes", required = false)
  val attributes: Set<ResidentialAttributeValue>? = null,
) : CreateRequest {

  override fun toNewEntity(createdBy: String, clock: Clock): ResidentialLocationJPA {
    return if (locationType == LocationType.CELL) {
      val location = CellJPA(
        id = null,
        prisonId = prisonId,
        code = code,
        locationType = locationType,
        pathHierarchy = code,
        description = description,
        residentialHousingType = residentialHousingType,
        comments = comments,
        orderWithinParentLocation = orderWithinParentLocation,
        active = true,
        updatedBy = createdBy,
        whenCreated = LocalDateTime.now(clock),
        whenUpdated = LocalDateTime.now(clock),
        deactivatedDate = null,
        deactivatedReason = null,
        proposedReactivationDate = null,
        childLocations = mutableListOf(),
        parent = null,
        capacity = capacity?.let { CapacityJPA(capacity = it.capacity, operationalCapacity = it.operationalCapacity) },
        certification = certification?.let {
          CertificationJPA(
            certified = it.certified,
            capacityOfCertifiedCell = it.capacityOfCertifiedCell,
          )
        },
      )
      attributes?.forEach { attribute ->
        location.addAttribute(attribute)
      }
      return location
    } else {
      ResidentialLocationJPA(
        id = null,
        prisonId = prisonId,
        code = code,
        locationType = locationType,
        pathHierarchy = code,
        description = description,
        residentialHousingType = residentialHousingType,
        comments = comments,
        orderWithinParentLocation = orderWithinParentLocation,
        active = true,
        updatedBy = createdBy,
        whenCreated = LocalDateTime.now(clock),
        whenUpdated = LocalDateTime.now(clock),
        deactivatedDate = null,
        deactivatedReason = null,
        proposedReactivationDate = null,
        childLocations = mutableListOf(),
        parent = null,
      )
    }
  }
}

@Schema(description = "Request to create a non-residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateNonResidentialLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 3)
  @field:Size(min = 3, message = "PrisonId cannot be blank")
  @field:Size(max = 3, message = "PrisonId must be 3 characters")
  override val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "ADJ", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  override val code: String,

  @Schema(description = "Location Type", example = "ADJUDICATION_ROOM", required = true)
  override val locationType: LocationType,

  @Schema(description = "Alternative description to display for location", example = "Adj Room", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val description: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  @field:Size(max = 255, message = "Comments must be less than 256 characters")
  override val comments: String? = null,

  @Schema(description = "Sequence of locations within the current parent location", example = "1", required = false)
  override val orderWithinParentLocation: Int? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  override val parentId: UUID? = null,

  @Schema(description = "Location Usage", required = false)
  val usage: Set<NonResidentialUsageDto>? = null,
) : CreateRequest {

  override fun toNewEntity(createdBy: String, clock: Clock): NonResidentialLocationJPA {
    val location = NonResidentialLocationJPA(
      id = null,
      prisonId = prisonId,
      code = code,
      locationType = locationType,
      pathHierarchy = code,
      description = description,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = true,
      updatedBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      whenUpdated = LocalDateTime.now(clock),
      deactivatedDate = null,
      deactivatedReason = null,
      reactivatedDate = null,
      childLocations = mutableListOf(),
      parent = null,
    )
    usage?.forEach { usage ->
      location.addUsage(usage.usageType, usage.capacity, usage.sequence)
    }
    return location
  }
}

/**
 * Request format to create a location
 */
@Schema(description = "Request to deactivate a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeactivationLocationRequest(
  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = true)
  val deactivationReason: DeactivatedReason,
  @Schema(description = "Proposed re-activation date", example = "2025-01-05", required = false)
  val reactivationDate: LocalDate? = null,
)
