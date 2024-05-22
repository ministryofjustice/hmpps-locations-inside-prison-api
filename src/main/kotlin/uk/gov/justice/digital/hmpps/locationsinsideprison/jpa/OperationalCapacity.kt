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
  var capacity: Int,
  var prisonId: String,
  var dateTime: LocalDateTime,
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

    return prisonId == other.prisonId
  }

  override fun hashCode(): Int {
    return prisonId.hashCode()
  }

  override fun toString(): String {
    return "Operational capacity(capacity=$capacity, prisonId=$prisonId, dateTime=$dateTime, approvedBy=$approvedBy )"
  }
}
