package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDTO

@Entity
class Capacity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  var capacity: Int = 0,
  var operationalCapacity: Int = 0,
) {
  fun toDto() = (
    CapacityDTO(
      capacity = capacity,
      operationalCapacity = operationalCapacity,
    )
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

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

  override fun toString(): String {
    return "Capacity(capacity=$capacity, operationalCapacity=$operationalCapacity)"
  }
}
