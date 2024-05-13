package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.Hibernate
import java.time.LocalDateTime

@Entity
class OperationalCapacity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  var capacity: Int = 0,
  var prisonId: String = "",
  var dateTime: LocalDateTime = LocalDateTime.now(),
  var approvedBy: String,
) {
  fun toDto() = (
    // TODO update when DTO will be created
    null
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as OperationalCapacity

    if (capacity != other.capacity) return false
    if (prisonId != other.prisonId) return false
    if (dateTime != other.dateTime) return false
    if (approvedBy != other.approvedBy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = capacity
    result = 31 * result + capacity
    return result
  }

  override fun toString(): String {
    return "Operational capacity(capacity=$capacity, prisonId=$prisonId, dateTime=$dateTime, approvedBy=$approvedBy )"
  }
}
