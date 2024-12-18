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
  val display: Boolean = false,
  val notUsed: Boolean = false,
) {
  OPERATIONAL_CAPACITY(description = "Working capacity", display = true),
  CAPACITY(description = "Maximum capacity", display = true),
  SPECIALIST_CELL_TYPE(description = "Cell type", display = true),
  USED_FOR(description = "Used for", display = true),
  DESCRIPTION(description = "Local name", display = true),
  CERTIFIED(description = "Certification", display = true),
  STATUS(description = "Status", display = true),
  DEACTIVATION_REASON(description = "Deactivation reason", display = true),
  CONVERTED_CELL_TYPE(description = "Non-residential room", display = true),

  CODE(description = "Code"),
  LOCATION_TYPE(description = "Location type"),
  RESIDENTIAL_HOUSING_TYPE(description = "Residential housing type"),
  CERTIFIED_CAPACITY(description = "Baseline certified capacity"),
  PARENT_LOCATION(description = "Parent location"),
  ORDER_WITHIN_PARENT_LOCATION(description = "Order within parent location"),
  COMMENTS(description = "Comments"),
  ATTRIBUTES(description = "Attributes"),
  PROPOSED_REACTIVATION_DATE(description = "Proposed reactivation date"),
  ACCOMMODATION_TYPE(description = "Accommodation type"),
  PLANET_FM_NUMBER(description = "Planet FM number"),
  PERMANENT_DEACTIVATION(description = "Permanent deactivation"),

  // non res only
  USAGE(description = "Usage", display = true),
  NON_RESIDENTIAL_CAPACITY(description = "Non residential capacity", display = true),

  ACTIVE(description = "Active", notUsed = true),
  DEACTIVATED_DATE(description = "Deactivated date", notUsed = true),
  DEACTIVATED_REASON(description = "Deactivated reason", notUsed = true),
  DEACTIVATED_REASON_DESCRIPTION(description = "Deactivated reason description", notUsed = true),
}
