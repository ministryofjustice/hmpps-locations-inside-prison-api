package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Capacity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  var capacity: Int = 0,
  var operationalCapacity: Int = 0,
  var currentOccupancy: Int = 0,
) {

  fun hasSpace(): Boolean {
    val capacity: Int = getActualCapacity()
    return currentOccupancy < capacity
  }

  private fun getActualCapacity(): Int {
    val useOperationalCapacity = operationalCapacity != 0
    return if (useOperationalCapacity) operationalCapacity else capacity
  }
}
