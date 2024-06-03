package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SortAttribute
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity as CapacityJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell as CellJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification as CertificationJPA
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

  @Schema(description = "Alternative description to display for location, (Not Cells)", example = "Wing A", required = false)
  val localName: String? = null,

  @Schema(description = "Additional comments that can be made about this location", example = "Not to be used", required = false)
  val comments: String? = null,

  @Schema(description = "Indicates if the location is permanently inactive", example = "false", required = true)
  val permanentlyInactive: Boolean = false,

  @Schema(description = "Reason for permanently deactivating", example = "Demolished", required = false)
  val permanentlyInactiveReason: String? = null,

  @Schema(description = "Capacity details of the location", required = false)
  val capacity: Capacity? = null,

  @Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  val certification: Certification? = null,

  @Schema(description = "Location Usage", required = false)
  val usage: List<NonResidentialUsageDto>? = null,

  @Schema(description = "Accommodation Types", required = false)
  val accommodationTypes: List<AccommodationType>? = null,

  @Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: List<SpecialistCellType>? = null,

  @Schema(description = "Usage For", required = false)
  val usedFor: List<UsedForType>? = null,

  @Schema(description = "Status of the location", example = "ACTIVE", required = true)
  val status: LocationStatus,

  @Schema(description = "Convert Cell Type", required = false)
  val convertedCellType: ConvertedCellType? = null,

  @Schema(description = "Convert Cell Type (Other)", required = false)
  val otherConvertedCellType: String? = null,

  @Schema(description = "Indicates the location is enabled", example = "true", required = true)
  val active: Boolean = true,

  @Schema(description = "Indicates the location in inactive as a parent is deactivated", example = "false", required = true)
  val deactivatedByParent: Boolean = false,

  @Schema(description = "Date the location was deactivated", example = "2023-01-23T12:23:00", required = false)
  val deactivatedDate: LocalDateTime? = null,

  @Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @Schema(description = "Staff username who deactivated the location", required = false)
  val deactivatedBy: String? = null,

  @Schema(description = "Proposed Date for location reactivation", example = "2026-01-24", required = false)
  val proposedReactivationDate: LocalDate? = null,

  @Schema(description = "Planet FM Reference", example = "2323/45M", required = false)
  val planetFmReference: String? = null,

  @Schema(description = "Top Level Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = true)
  val topLevelId: UUID,

  @Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @Schema(description = "Parent Location", required = false)
  val parentLocation: Location? = null,

  @Schema(description = "Number of inactive cells below this location", required = false)
  val inactiveCells: Int? = null,

  @Schema(description = "Child Locations", required = false)
  val childLocations: List<Location>? = null,

  @Schema(description = "History of changes", required = false)
  val changeHistory: List<ChangeHistory>? = null,

  @Schema(description = "Staff username who last changed the location", required = true)
  val lastModifiedBy: String,

  @Schema(description = "Date and time of the last change", required = true)
  val lastModifiedDate: LocalDateTime,

) : SortAttribute {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  override fun getKey(): String {
    return "$prisonId-$pathHierarchy"
  }

  @Schema(description = "Indicates if the location is a residential location", example = "true", required = true)
  fun isResidential(): Boolean {
    return accommodationTypes != null
  }

  @JsonIgnore
  fun getSubLocations(): List<Location> {
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
  @Schema(description = "Max capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int = 0,
  @Schema(description = "Working capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int = 0,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Capacity

    if (maxCapacity != other.maxCapacity) return false
    if (workingCapacity != other.workingCapacity) return false

    return true
  }

  override fun hashCode(): Int {
    var result = maxCapacity
    result = 31 * result + workingCapacity
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

/**
 * Request format to create a residential location
 */
@Schema(description = "Request to create a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateResidentialLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be up to 12 characters")
  val code: String,

  @Schema(description = "Accommodation Type", required = false, example = "NORMAL_ACCOMMODATION")
  val accommodationType: AccommodationType,

  @Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: ResidentialLocationType,

  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  val localName: String? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,

  @Schema(description = "Capacity of the residential location", required = false)
  val capacity: Capacity? = null,

  @Schema(description = "Certified status of the residential location", required = false, defaultValue = "false")
  val certified: Boolean = false,

  @Schema(description = "Used For Types", required = false)
  val usedFor: Set<UsedForType>? = null,

  @Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: Set<SpecialistCellType>? = null,
) {

  fun toNewEntity(createdBy: String, clock: Clock): ResidentialLocationJPA {
    return if (isCell()) {
      val location = CellJPA(
        prisonId = prisonId,
        code = code,
        locationType = locationType.baseType,
        pathHierarchy = code,
        localName = localName,
        residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
        createdBy = createdBy,
        whenCreated = LocalDateTime.now(clock),
        childLocations = mutableListOf(),
        accommodationType = accommodationType,
        capacity = capacity?.let { CapacityJPA(maxCapacity = it.maxCapacity, workingCapacity = it.workingCapacity) },
        certification =
        CertificationJPA(
          certified = certified,
          capacityOfCertifiedCell = 0,
        ),
      )
      usedFor?.forEach {
        location.addUsedFor(it, createdBy, clock)
      } ?: location.addUsedFor(UsedForType.STANDARD_ACCOMMODATION, createdBy, clock)

      specialistCellTypes?.forEach {
        location.addSpecialistCellType(it, createdBy, clock)
      }
      return location
    } else {
      ResidentialLocationJPA(
        id = null,
        prisonId = prisonId,
        code = code,
        locationType = locationType.baseType,
        pathHierarchy = code,
        localName = localName,
        residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
        createdBy = createdBy,
        whenCreated = LocalDateTime.now(clock),
        childLocations = mutableListOf(),
      )
    }
  }

  fun isCell() = locationType == ResidentialLocationType.CELL
}

@Schema(description = "Request to create a non-residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateNonResidentialLocationRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @Schema(description = "Code of the location", required = true, example = "ADJ", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  val code: String,

  @Schema(description = "Location Type", example = "ADJUDICATION_ROOM", required = true)
  val locationType: NonResidentialLocationType,

  @Schema(description = "Alternative description to display for location", example = "Adj Room", required = false)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  val localName: String? = null,

  @Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,

  @Schema(description = "Location Usage", required = false)
  val usage: Set<NonResidentialUsageDto>? = null,
) {

  fun toNewEntity(createdBy: String, clock: Clock): NonResidentialLocationJPA {
    val location = NonResidentialLocationJPA(
      id = null,
      prisonId = prisonId,
      code = code,
      locationType = locationType.baseType,
      pathHierarchy = code,
      localName = localName,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = mutableListOf(),
    )
    usage?.forEach { usage ->
      location.addUsage(usage.usageType, usage.capacity, usage.sequence)
    }
    return location
  }
}

/**
 * Request format temporarily deactivating a location
 */
@Schema(description = "Request to temporarily deactivate a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TemporaryDeactivationLocationRequest(
  @Schema(description = "Reason for temporary deactivation", example = "MOTHBALLED", required = true)
  val deactivationReason: DeactivatedReason,
  @Schema(description = "Proposed re-activation date", example = "2025-01-05", required = false)
  val proposedReactivationDate: LocalDate? = null,
  @Schema(description = "Planet FM reference", example = "23423TH/5", required = false)
  @field:Size(max = 60, message = "Planet FM reference cannot be more than 60 characters")
  val planetFmReference: String? = null,
)

/**
 * Request format permanently deactivating a location
 */
@Schema(description = "Request to permanently deactivate a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PermanentDeactivationLocationRequest(
  @Schema(description = "Reason for permanent deactivation", example = "Wing demolished", required = true)
  @field:Size(max = 200, message = "Reason for permanent deactivation cannot be more than 200 characters")
  val reason: String,
)
