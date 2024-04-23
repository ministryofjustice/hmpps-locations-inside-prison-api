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
import java.time.LocalDateTime

@Entity
class LocationHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  val location: Location,

  @Enumerated(EnumType.STRING)
  val attributeName: LocationAttribute,

  val oldValue: String? = null,

  val newValue: String? = null,

  val amendedBy: String,

  val amendedDate: LocalDateTime,

) : Comparable<LocationHistory> {

  fun toDto() =
    ChangeHistory(
      attribute = attributeName.description,
      oldValue = oldValue,
      newValue = newValue,
      amendedBy = amendedBy,
      amendedDate = amendedDate,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as LocationHistory

    if (location != other.location) return false
    if (amendedDate != other.amendedDate) return false
    if (attributeName != other.attributeName) return false
    if (oldValue != other.oldValue) return false
    if (newValue != other.newValue) return false
    if (amendedBy != other.amendedBy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + amendedDate.hashCode()
    result = 31 * result + attributeName.hashCode()
    result = 31 * result + (oldValue?.hashCode() ?: 0)
    result = 31 * result + (newValue?.hashCode() ?: 0)
    result = 31 * result + amendedBy.hashCode()
    return result
  }

  override fun compareTo(other: LocationHistory) =
    compareValuesBy(this, other, { it.location.id }, { it.amendedDate }, { it.attributeName }, { it.oldValue }, { it.newValue }, { it.amendedBy })

  override fun toString(): String {
    return "Changed $attributeName from $oldValue --> $newValue, on $amendedDate)"
  }
}
enum class LocationAttribute(
  val description: String,
) {
  CODE("Code"),
  LOCATION_TYPE("Location Type"),
  RESIDENTIAL_HOUSING_TYPE("Residential Housing Type"),
  OPERATIONAL_CAPACITY("Working Capacity"),
  CAPACITY("Max Capacity"),
  CERTIFIED_CAPACITY("Baseline Certified Capacity"),
  CERTIFIED("Certified"),
  PARENT_LOCATION("Parent Location"),
  ORDER_WITHIN_PARENT_LOCATION("Order within parent location"),
  COMMENTS("Comments"),
  DESCRIPTION("Local Name"),
  ATTRIBUTES("Attributes"),
  USAGE("Usage"),
  NON_RESIDENTIAL_CAPACITY("Non Residential Capacity"),
  ACTIVE("Active"),
  DEACTIVATED_DATE("Deactivated Date"),
  DEACTIVATED_REASON("Deactivated Reason"),
  PROPOSED_REACTIVATION_DATE("Proposed Reactivation Date"),
  ACCOMMODATION_TYPE("Accommodation Type"),
  SPECIALIST_CELL_TYPE("Specialist Cell Type"),
  SECURITY_CATEGORY("Security Category"),
  USED_FOR("Used For"),
}
