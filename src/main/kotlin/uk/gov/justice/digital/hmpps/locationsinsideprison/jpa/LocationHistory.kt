package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TransactionDetail
import java.time.LocalDateTime

@Entity
class LocationHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  val location: Location,

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  val linkedTransaction: LinkedTransaction? = null,

  @Enumerated(EnumType.STRING)
  val attributeName: LocationAttribute,

  val oldValue: String? = null,

  val newValue: String? = null,

  val amendedBy: String,

  val amendedDate: LocalDateTime,

) : Comparable<LocationHistory> {

  fun toDto() = ChangeHistory(
    transactionId = linkedTransaction?.transactionId,
    transactionType = linkedTransaction?.transactionType,
    attribute = attributeName.description,
    oldValues = oldValue?.let { listOf(it) },
    newValues = newValue?.let { listOf(it) },
    amendedBy = amendedBy,
    amendedDate = amendedDate,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as LocationHistory

    if (linkedTransaction != other.linkedTransaction) return false
    if (location != other.location) return false
    if (amendedDate != other.amendedDate) return false
    if (attributeName != other.attributeName) return false
    if (oldValue != other.oldValue) return false
    if (newValue != other.newValue) return false
    if (amendedBy != other.amendedBy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = linkedTransaction.hashCode()
    result = 31 * result + location.hashCode()
    result = 31 * result + amendedDate.hashCode()
    result = 31 * result + attributeName.hashCode()
    result = 31 * result + (oldValue?.hashCode() ?: 0)
    result = 31 * result + (newValue?.hashCode() ?: 0)
    result = 31 * result + amendedBy.hashCode()
    return result
  }

  override fun compareTo(other: LocationHistory) = compareValuesBy(this, other, { it.linkedTransaction?.transactionId }, { it.location.id }, { it.amendedDate }, { it.attributeName }, { it.oldValue }, { it.newValue }, { it.amendedBy })

  override fun toString(): String = "${linkedTransaction?.transactionId ?: "NONE"}: Changed $attributeName from $oldValue --> $newValue, on $amendedDate)"
}
enum class LocationAttribute(
  val description: String,
  val display: Boolean = false,
  val displayOrder: Int = 99,
  val notUsed: Boolean = false,
) {

  // These are all returned as history changes
  STATUS(description = "Status", display = true, displayOrder = 0),
  CERTIFICATION(description = "Certification", display = true, displayOrder = 5),
  ACCOMMODATION_TYPE(description = "Accommodation type", display = true, displayOrder = 10),
  USED_FOR(description = "Used for", display = true, displayOrder = 15),
  SPECIALIST_CELL_TYPE(description = "Cell type", display = true, displayOrder = 20),
  CONVERTED_CELL_TYPE(description = "Non-residential room", display = true, displayOrder = 25),
  WORKING_CAPACITY(description = "Working capacity", display = true, displayOrder = 30),
  MAX_CAPACITY(description = "Maximum capacity", display = true, displayOrder = 35),
  DEACTIVATION_REASON(description = "Deactivation reason", display = true, displayOrder = 40),
  PROPOSED_REACTIVATION_DATE(description = "Estimated reactivation date", display = true, displayOrder = 45),
  PLANET_FM_NUMBER(description = "Planet FM reference number", display = true, displayOrder = 50),
  LOCAL_NAME(description = "Local name", display = true, displayOrder = 55),

  // non res only
  USAGE(description = "Usage", display = true),
  NON_RESIDENTIAL_CAPACITY(description = "Non residential capacity", display = true),
  INTERNAL_MOVEMENT_ALLOWED(description = "Internal movement allowed", display = true),

  // These are recorded but not returned as history changes
  CODE(description = "Code"),
  LOCATION_TYPE(description = "Location type"),
  RESIDENTIAL_HOUSING_TYPE(description = "Residential housing type"),
  CERTIFIED_CAPACITY(description = "Certified normal accommodation"),
  PARENT_LOCATION(description = "Parent location"),
  ORDER_WITHIN_PARENT_LOCATION(description = "Order within parent location"),
  COMMENTS(description = "Comments"),
  ATTRIBUTES(description = "Attributes"),
  PERMANENT_DEACTIVATION(description = "Permanent deactivation"),
  LOCATION_CREATED(description = "Location created"),

  // These are not recorded
  ACTIVE(description = "Active", notUsed = true),
  DEACTIVATED_DATE(description = "Deactivated date", notUsed = true),
  DEACTIVATED_REASON(description = "Deactivated reason", notUsed = true),
  DEACTIVATED_REASON_DESCRIPTION(description = "Deactivated reason description", notUsed = true),
}

fun toGroupedTx(
  attribute: LocationAttribute,
  history: List<LocationHistory>,
): TransactionDetail {
  val firstRecord = history.first()
  return TransactionDetail(
    locationId = firstRecord.location.id!!,
    locationKey = firstRecord.location.getKey(),
    amendedBy = firstRecord.amendedBy,
    amendedDate = firstRecord.amendedDate,
    attributeCode = attribute,
    attribute = attribute.description,
    oldValues = history.mapNotNull { it.oldValue }.ifEmpty { null },
    newValues = history.mapNotNull { it.newValue }.ifEmpty { null },
  )
}
