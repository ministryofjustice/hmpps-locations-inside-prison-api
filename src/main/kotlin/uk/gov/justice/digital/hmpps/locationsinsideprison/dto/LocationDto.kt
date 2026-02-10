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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceFamilyType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.getVirtualLocationCodes
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.identifyNonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SortAttribute
import java.lang.Boolean.FALSE
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity as CapacityJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell as CellJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation as NonResidentialLocationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation as ResidentialLocationJPA
@Schema(description = "Location Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Location(
  @param:Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Location Code", example = "001", required = true)
  val code: String,

  @param:Schema(description = "Cell mark", example = "A1", required = false)
  val cellMark: String? = null,

  @param:Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @param:Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: LocationType,

  @param:Schema(
    description = "Alternative description to display for location, (Not Cells)",
    example = "Wing A",
    required = false,
  )
  val localName: String? = null,

  @param:Schema(description = "The structure of the wing", required = false)
  val wingStructure: List<ResidentialStructuralType>? = null,

  @param:Schema(
    description = "Additional comments that can be made about this location",
    example = "Not to be used",
    required = false,
  )
  val comments: String? = null,

  @param:Schema(description = "Indicates if the location is permanently inactive", example = "false", required = true)
  val permanentlyInactive: Boolean = false,

  @param:Schema(description = "Reason for permanently deactivating", example = "Demolished", required = false)
  val permanentlyInactiveReason: String? = null,

  @param:Schema(description = "Capacity details of the location", required = false)
  val capacity: Capacity? = null,

  @param:Schema(description = "Pending changes of draft or pending approval location", required = false)
  val pendingChanges: PendingChangeDto? = null,

  @param:Schema(description = "When a cell is inactive, show the active working capacity value", required = false)
  val oldWorkingCapacity: Int? = null,

  @param:Schema(
    description = "Indicates that this location is certified for use as a cell",
    example = "true",
    required = false,
  )
  val certifiedCell: Boolean? = null,

  @param:Schema(description = "Indicates that this location is certified for use as a residential location", required = false)
  @Deprecated("Use certified instead")
  val certification: Certification? = null,

  @param:Schema(description = "Location Usage", required = false)
  val usage: List<NonResidentialUsageDto>? = null,

  @param:Schema(description = "Services that use this location", required = false)
  val servicesUsingLocation: List<ServiceUsingLocationDto>? = null,

  @param:Schema(description = "Indicates that this location can used for internal movements", required = false)
  val internalMovementAllowed: Boolean? = null,

  @param:Schema(description = "Accommodation Types", required = false)
  val accommodationTypes: List<AccommodationType>? = null,

  @param:Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: List<SpecialistCellType>? = null,

  @param:Schema(description = "Usage For", required = false)
  val usedFor: List<UsedForType>? = null,

  @param:Schema(description = "Status of the location", example = "ACTIVE", required = true)
  val status: DerivedLocationStatus,

  @param:Schema(description = "Location is locked", example = "false", required = true)
  val locked: Boolean = false,

  @param:Schema(description = "Convert Cell Type", required = false)
  val convertedCellType: ConvertedCellType? = null,

  @param:Schema(description = "Convert Cell Type (Other)", required = false)
  val otherConvertedCellType: String? = null,

  @param:Schema(description = "Indicates the location is enabled", example = "true", required = true, deprecated = true)
  val active: Boolean = true,

  @param:Schema(description = "In-cell sanitation", required = false, example = "true")
  val inCellSanitation: Boolean? = null,

  @param:Schema(
    description = "Indicates the location in inactive as a parent is deactivated",
    example = "false",
    required = true,
  )
  val deactivatedByParent: Boolean = false,

  @param:Schema(description = "Date the location was deactivated", example = "2023-01-23T12:23:00", required = false)
  val deactivatedDate: LocalDateTime? = null,

  @param:Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @param:Schema(
    description = "For OTHER deactivation reason, a free text comment is provided",
    example = "Window damage",
    required = false,
  )
  val deactivationReasonDescription: String? = null,

  @param:Schema(description = "Staff username who deactivated the location", required = false)
  val deactivatedBy: String? = null,

  @param:Schema(description = "Estimated reactivation date for location reactivation", example = "2026-01-24", required = false)
  val proposedReactivationDate: LocalDate? = null,

  @param:Schema(description = "Planet FM reference number", example = "2323/45M", required = false)
  val planetFmReference: String? = null,

  @param:Schema(description = "Top Level Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = true)
  val topLevelId: UUID,

  @param:Schema(
    description = "Current Level within hierarchy, starts at 1, e.g Wing = 1",
    examples = ["1", "2", "3"],
    required = true,
  )
  val level: Int,

  @param:Schema(description = "Indicates this is the lowest level, often a cell", example = "false", required = true)
  val leafLevel: Boolean,

  @param:Schema(description = "Location Id where approvals can be requested, below this level approval request will not be allowed", example = "57718979-573c-433a-9e51-2d83f087c11c", required = false)
  val topLevelApprovalLocationId: UUID? = null,

  @param:Schema(description = "Indicates that this location this one has a pending approval, the approval will be for the location held in topLevelApprovalLocationId`", example = "57818979-573c-433a-9e51-2d83f087c11c", required = false)
  val pendingApprovalRequestId: UUID? = null,

  @param:Schema(description = "Reason for the last deactivation change", example = "Cell damaged", required = false)
  val lastDeactivationReasonForChange: String? = null,

  @param:Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @param:Schema(description = "Parent Location", required = false)
  val parentLocation: Location? = null,

  @param:Schema(description = "Number of inactive cells below this location", required = false)
  val inactiveCells: Int? = null,

  @param:Schema(description = "Total number of non-structural locations are below this level, e.g. cells and rooms")
  val numberOfCellLocations: Int? = null,

  @param:Schema(description = "Child Locations", required = false)
  val childLocations: List<Location>? = null,

  @param:Schema(description = "History of changes", required = false)
  val changeHistory: List<ChangeHistory>? = null,

  @param:Schema(description = "A list of transactions applied to this location", required = false)
  val transactionHistory: List<TransactionHistory>? = null,

  @param:Schema(description = "Staff username who last changed the location", required = true)
  val lastModifiedBy: String,

  @param:Schema(description = "Date and time of the last change", required = true)
  val lastModifiedDate: LocalDateTime,

) : SortAttribute {
  @Schema(description = "Business Key for a location", example = "MDI-A-1-001", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"

  @JsonIgnore
  override fun getSortName(): String = localName?.capitalizeWords() ?: pathHierarchy

  @Schema(description = "Indicates if the location is a residential location", example = "true", required = true)
  fun isResidential(): Boolean = accommodationTypes != null && convertedCellType == null

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Location

    if (prisonId != other.prisonId) return false
    if (pathHierarchy != other.pathHierarchy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = prisonId.hashCode()
    result = 31 * result + pathHierarchy.hashCode()
    return result
  }
}

@Schema(description = "Pending changes")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PendingChangeDto(
  @param:Schema(description = "Pending max capacity", example = "2", required = false)
  val maxCapacity: Int? = null,

  @param:Schema(description = "Pending working capacity", example = "1", required = false)
  val workingCapacity: Int? = null,

  @param:Schema(description = "Pending CNA", example = "2", required = false)
  val certifiedNormalAccommodation: Int? = null,

  @param:Schema(description = "Pending cell mark of the location", required = false, example = "A1", minLength = 1)
  val cellMark: String? = null,

  @param:Schema(description = "Pending in-cell sanitation", required = false, example = "true")
  val inCellSanitation: Boolean? = null,

)

@Schema(description = "Service that uses a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServiceUsingLocationDto(
  val serviceType: ServiceType,
  val serviceFamilyType: ServiceFamilyType,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ServiceUsingLocationDto

    return serviceType == other.serviceType
  }

  override fun hashCode(): Int = serviceType.hashCode()
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
  @param:Schema(description = "Max capacity of the cell", example = "2", required = true)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int = 0,
  @param:Schema(description = "Working capacity of the cell", example = "1", required = true)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int = 0,
  @param:Schema(description = "CNA of the cell", example = "2", required = false)
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  val certifiedNormalAccommodation: Int? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Capacity

    if (maxCapacity != other.maxCapacity) return false
    if (workingCapacity != other.workingCapacity) return false
    if (certifiedNormalAccommodation != other.certifiedNormalAccommodation) return false

    return true
  }

  override fun hashCode(): Int {
    var result = maxCapacity
    result = 31 * result + workingCapacity
    result = 31 * result + (certifiedNormalAccommodation ?: 0)
    return result
  }
}

@Schema(description = "Change History")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChangeHistory(
  @param:Schema(description = "Transaction ID", example = "019464e9-05da-77b3-810b-887e199d8190", required = false)
  val transactionId: UUID? = null,

  @param:Schema(description = "Transaction type", example = "CAPACITY_CHANGE", required = false)
  val transactionType: TransactionType? = null,

  @param:Schema(description = "Location Attribute", example = "Location Type", required = true)
  val attribute: String,

  @param:Schema(description = "Previous values of this attribute", example = "[\"Dry cell\",\"Safe cell\"]", required = false)
  val oldValues: List<String>? = null,

  @param:Schema(description = "New values of this attribute", example = "[\"Dry cell\",\"Safe cell\"]", required = false)
  val newValues: List<String>? = null,

  @param:Schema(description = "User who made the change", example = "user", required = true)
  val amendedBy: String,

  @param:Schema(description = "Date the change was made", example = "2023-01-23T10:15:30", required = true)
  val amendedDate: LocalDateTime,
)

@Schema(description = "Transaction Detail")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransactionDetail(
  @param:Schema(description = "Location Id", example = "019483f5-fee7-7ed0-924c-3ee4b2b51904", required = true)
  val locationId: UUID,

  @param:Schema(description = "Location key", example = "BXI-1-1-001", required = true)
  val locationKey: String,

  @param:Schema(description = "Attribute Code", required = true)
  val attributeCode: LocationAttribute,

  @param:Schema(description = "Location Attribute", example = "Location Type", required = true)
  val attribute: String,

  @param:Schema(description = "User who made the change", example = "user", required = true)
  val amendedBy: String,

  @param:Schema(description = "Date the change was made", example = "2023-01-23T10:15:30", required = true)
  val amendedDate: LocalDateTime,

  @param:Schema(description = "Previous values of this attribute", example = "[\"Dry cell\",\"Safe cell\"]", required = false)
  val oldValues: List<String>? = null,

  @param:Schema(description = "New values of this attribute", example = "[\"Dry cell\",\"Safe cell\"]", required = false)
  val newValues: List<String>? = null,
) {
  fun toChangeHistory(transactionHistory: TransactionHistory): ChangeHistory? {
    val distinctOldValues = oldValues?.distinct()
    val distinctNewValues = newValues?.distinct()
    if (distinctOldValues != distinctNewValues) {
      return ChangeHistory(
        transactionId = transactionHistory.transactionId,
        transactionType = transactionHistory.transactionType,
        attribute = attribute,
        oldValues = distinctOldValues,
        newValues = distinctNewValues,
        amendedBy = amendedBy,
        amendedDate = amendedDate,
      )
    }
    return null
  }
}

@Schema(description = "Transaction history for location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TransactionHistory(

  @param:Schema(description = "Unique transaction ID", example = "019464e9-05da-77b3-810b-887e199d8190", required = true)
  val transactionId: UUID,

  @param:Schema(description = "Type of transaction", example = "CAPACITY_CHANGE", required = true)
  val transactionType: TransactionType,

  @param:Schema(description = "Prison ID of the transaction", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "Description of the transaction", example = "Working capacity changed from 0 to 1", required = true)
  val transactionDetail: String,

  @param:Schema(description = "User who invoked the change", example = "STAFF_USER1", required = true)
  val transactionInvokedBy: String,

  @param:Schema(description = "Date and time the transaction started", required = true)
  val txStartTime: LocalDateTime,

  @param:Schema(description = "Date and time the transaction ended", required = true)
  var txEndTime: LocalDateTime? = null,

  @param:Schema(description = "The list of changes that were made in the transaction", required = true)
  val transactionDetails: List<TransactionDetail>,
) {
  fun toChangeHistory(): List<ChangeHistory> = transactionDetails.mapNotNull { it.toChangeHistory(this) }
}

@Schema(description = "Certification")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Deprecated("CNA and certified marker have moved under capacity and location")
data class Certification(
  @param:Schema(
    description = "Indicates that this location is certified for use as a residential location",
    example = "true",
    required = false,
  )
  val certified: Boolean = false,
  @param:Schema(description = "Old name for CNA (Certified normal accommodation)", example = "1", required = false, deprecated = true)
  @Deprecated("Use certifiedNormalAccommodation instead")
  val capacityOfCertifiedCell: Int = 0,

  @param:Schema(description = "CNA (Certified normal accommodation)", example = "1", required = false)
  @Deprecated("Use certifiedNormalAccommodation in capacity instead")
  val certifiedNormalAccommodation: Int? = null,
) {

  @JsonIgnore
  fun getCNA() = certifiedNormalAccommodation ?: capacityOfCertifiedCell

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Certification

    if (certified != other.certified) return false
    if (getCNA() != other.getCNA()) return false

    return true
  }

  override fun hashCode(): Int {
    var result = certified.hashCode()
    result = 31 * result + getCNA()
    return result
  }
}

/**
 * Request format to create a residential location
 */
@Schema(description = "Request to create a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateResidentialLocationRequest(
  @param:Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @param:Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be up to 12 characters")
  val code: String,

  @param:Schema(description = "Cell mark of the location", required = false, example = "A1", minLength = 1)
  @field:Size(min = 1, message = "Mark cannot be blank")
  @field:Size(max = 12, message = "Mark must be up to 12 characters")
  val cellMark: String? = null,

  @param:Schema(description = "Accommodation Type", required = false, example = "NORMAL_ACCOMMODATION")
  val accommodationType: AccommodationType,

  @param:Schema(description = "Location Type", example = "CELL", required = true)
  val locationType: ResidentialLocationType,

  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Local name must be less than 81 characters")
  val localName: String? = null,

  @param:Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,

  @param:Schema(description = "Key of parent location (can be used instead of parentId)", example = "MDI-B-1", required = false)
  val parentLocationKey: String? = null,

  @param:Schema(description = "Capacity of the residential location", required = false)
  val capacity: Capacity? = null,

  @param:Schema(description = "Certified status of the residential location", required = false, defaultValue = "false")
  val certified: Boolean = false,

  @param:Schema(description = "Used For Types", required = false)
  val usedFor: Set<UsedForType>? = null,

  @param:Schema(description = "Specialist Cell Types", required = false)
  val specialistCellTypes: Set<SpecialistCellType>? = null,

  @param:Schema(description = "CNA value", required = false, defaultValue = "0")
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  @Deprecated("Use certifiedNormalAccommodation in capacity instead")
  val certifiedNormalAccommodation: Int = 0,

  @param:Schema(description = "In-cell sanitation", required = false, defaultValue = "false")
  val inCellSanitation: Boolean = false,
) {

  fun toNewEntity(createdBy: String, clock: Clock, linkedTransaction: LinkedTransaction, createInDraft: Boolean = false, parentLocation: LocationJPA? = null) = if (isCell()) {
    val request = this
    CellJPA(
      prisonId = prisonId,
      code = code,
      cellMark = cellMark,
      locationType = locationType.baseType,
      pathHierarchy = code,
      localName = localName,
      status = if (createInDraft) LocationStatus.DRAFT else LocationStatus.ACTIVE,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = sortedSetOf(),
      accommodationType = accommodationType,
      capacity = capacity?.let { CapacityJPA(maxCapacity = it.maxCapacity, workingCapacity = it.workingCapacity, certifiedNormalAccommodation = it.certifiedNormalAccommodation ?: certifiedNormalAccommodation) },
      inCellSanitation = inCellSanitation,
      certifiedCell = if (createInDraft) false else certified,
    ).apply {
      if (request.accommodationType == AccommodationType.NORMAL_ACCOMMODATION) {
        addUsedFor(UsedForType.STANDARD_ACCOMMODATION, createdBy, clock, linkedTransaction)
      }
      request.usedFor?.forEach {
        addUsedFor(it, createdBy, clock, linkedTransaction)
      } ?: request.specialistCellTypes?.forEach {
        addSpecialistCellType(it, userOrSystemInContext = createdBy, clock = clock, linkedTransaction = linkedTransaction)
      }
      parentLocation?.let { setParent(it) }
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = createdBy,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
    }
  } else if (code in getVirtualLocationCodes()) {
    VirtualResidentialLocation(
      prisonId = prisonId,
      code = code,
      pathHierarchy = code,
      localName = localName,
      status = if (createInDraft) LocationStatus.DRAFT else LocationStatus.ACTIVE,
      capacity = capacity?.let { CapacityJPA(maxCapacity = it.maxCapacity, workingCapacity = it.workingCapacity) },
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = sortedSetOf(),
    ).apply {
      parentLocation?.let { setParent(it) }
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = createdBy,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
    }
  } else {
    ResidentialLocationJPA(
      prisonId = prisonId,
      code = code,
      locationType = locationType.baseType,
      pathHierarchy = code,
      status = if (createInDraft) LocationStatus.DRAFT else LocationStatus.ACTIVE,
      localName = localName,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = sortedSetOf(),
    ).apply {
      parentLocation?.let { setParent(it) }
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = createdBy,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
    }
  }

  fun isCell() = locationType == ResidentialLocationType.CELL
}

@Schema(description = "Request to create or update non-residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateOrUpdateNonResidentialLocationRequest(
  @param:Schema(description = "Description of the non-residential locations, mandatory for create", example = "Adj Room", required = false)
  @field:Size(min = 1, max = 80, message = "Local name must be between 1 and 80 characters")
  val localName: String? = null,

  @param:Schema(description = "Services that use this location", required = true)
  val servicesUsingLocation: Set<ServiceType> = emptySet(),

  @param:Schema(description = "Status, if false will be marked as inactive, true will make active or null untouched", required = false, example = "true")
  val active: Boolean? = null,
) {
  fun toNewEntity(
    prisonId: String,
    code: String,
    createdBy: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) = NonResidentialLocationJPA(
    id = null,
    prisonId = prisonId,
    code = code,
    locationType = identifyNonResidentialLocationType(servicesUsingLocation).baseType,
    pathHierarchy = code,
    status = if (FALSE == active) LocationStatus.INACTIVE else LocationStatus.ACTIVE,
    localName = localName,
    createdBy = createdBy,
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
    internalMovementAllowed = isInternalMovement(serviceTypes = servicesUsingLocation),
  ).apply {
    setServices(servicesUsingLocation, this)
    addHistory(
      attributeName = LocationAttribute.LOCATION_CREATED,
      oldValue = null,
      newValue = getKey(),
      amendedBy = createdBy,
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = linkedTransaction,
    )
  }
}

@Schema(description = "Request to create a non-residential location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateNonResidentialLocationRequest(
  @param:Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @param:Schema(description = "Code of the location", required = true, example = "ADJ", minLength = 1)
  @field:Size(min = 1, message = "Code cannot be blank")
  @field:Size(max = 12, message = "Code must be no more than 12 characters")
  val code: String,

  @param:Schema(description = "Location Type", example = "ADJUDICATION_ROOM", required = true)
  val locationType: NonResidentialLocationType,

  @param:Schema(description = "Alternative description to display for location", example = "Adj Room", required = false)
  @field:Size(max = 80, message = "Local name must be less than 81 characters")
  val localName: String? = null,

  @param:Schema(description = "ID of parent location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val parentId: UUID? = null,

  @param:Schema(description = "Services that use this location", required = false)
  val servicesUsingLocation: Set<ServiceType>? = null,

  @param:Schema(description = "Non residential location active, if false will be marked as inactive", required = false, defaultValue = "true", example = "true")
  val active: Boolean = true,
) {

  fun toNewEntity(createdBy: String, clock: Clock, linkedTransaction: LinkedTransaction, parentLocation: LocationJPA? = null) = NonResidentialLocationJPA(
    id = null,
    prisonId = prisonId,
    code = code,
    locationType = locationType.baseType,
    pathHierarchy = code,
    status = if (active) LocationStatus.ACTIVE else LocationStatus.INACTIVE,
    localName = localName,
    createdBy = createdBy,
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
    internalMovementAllowed = servicesUsingLocation?.let { isInternalMovement(serviceTypes = servicesUsingLocation) } ?: false,
  ).apply {
    parentLocation?.let { setParent(it) }
    servicesUsingLocation?.let { setServices(servicesUsingLocation, this) }
    addHistory(
      attributeName = LocationAttribute.LOCATION_CREATED,
      oldValue = null,
      newValue = getKey(),
      amendedBy = createdBy,
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = linkedTransaction,
    )
  }
}

private fun setServices(
  serviceTypes: Set<ServiceType>,
  location: NonResidentialLocationJPA,
) {
  serviceTypes.forEach { serviceType ->
    val usageType = serviceType.nonResidentialUsageType
    location.addUsage(usageType, 99)
    location.addService(serviceType)
  }
}

fun isInternalMovement(serviceTypes: Set<ServiceType>) = serviceTypes.find { it == ServiceType.INTERNAL_MOVEMENTS } != null

@Schema(description = "Request to temporarily deactivate a location - used in bulk updates")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BasicTemporaryDeactivationRequest(
  @param:Schema(description = "Reason for temporary deactivation", example = "MOTHBALLED", required = true)
  val deactivationReason: DeactivatedReason,
  @param:Schema(
    description = "Additional information on deactivation, for OTHER DeactivatedReason must be provided",
    example = "Window broken",
    required = false,
  )
  @field:Size(max = 255, message = "Other deactivation reason cannot be more than 255 characters")
  val deactivationReasonDescription: String? = null,
  @param:Schema(description = "Estimated reactivation date", example = "2025-01-05", required = false)
  val proposedReactivationDate: LocalDate? = null,
  @param:Schema(description = "Planet FM reference number", example = "23423TH/5", required = false)
  @field:Size(max = 60, message = "Planet FM reference number cannot be more than 60 characters")
  val planetFmReference: String? = null,
)

/**
 * Request format temporarily deactivating a location
 */
@Schema(description = "Request to temporarily deactivate a location, optionally indicating certification approval required")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TemporaryDeactivationLocationRequest(
  @param:Schema(description = "Reason for temporary deactivation", example = "MOTHBALLED", required = true)
  val deactivationReason: DeactivatedReason,
  @param:Schema(
    description = "Additional information on deactivation, for OTHER DeactivatedReason must be provided",
    example = "Window broken",
    required = false,
  )
  @field:Size(max = 255, message = "Other deactivation reason cannot be more than 255 characters")
  val deactivationReasonDescription: String? = null,
  @param:Schema(description = "Estimated reactivation date", example = "2025-01-05", required = false)
  val proposedReactivationDate: LocalDate? = null,
  @param:Schema(description = "Planet FM reference number", example = "23423TH/5", required = false)
  @field:Size(max = 60, message = "Planet FM reference number cannot be more than 60 characters")
  val planetFmReference: String? = null,
  @param:Schema(description = "The deactivation needs to be approved, if false (default) it will be classed as a short term temporary deactivation", example = "false", required = false)
  val requiresApproval: Boolean = false,
  @param:Schema(description = "Explanation of why the capacity need to be decreased", example = "The cell is damaged and will be take 6 months to repair", required = false)
  val reasonForChange: String? = null,
) {
  fun toBasicDeactivation() = BasicTemporaryDeactivationRequest(
    deactivationReason = deactivationReason,
    deactivationReasonDescription = deactivationReasonDescription,
    proposedReactivationDate = proposedReactivationDate,
    planetFmReference = planetFmReference,
  )
}

/**
 * Request format permanently deactivating a location
 */
@Schema(description = "Request to permanently deactivate a location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PermanentDeactivationLocationRequest(
  @param:Schema(description = "Reason for permanent deactivation", example = "Wing demolished", required = true)
  @field:Size(max = 200, message = "Reason for permanent deactivation cannot be more than 200 characters")
  val reason: String,
)

fun String.capitalizeWords(delimiter: String = " ") = split(delimiter).joinToString(delimiter) { word ->

  val smallCaseWord = word.lowercase()
  smallCaseWord.replaceFirstChar(Char::titlecaseChar)
}
