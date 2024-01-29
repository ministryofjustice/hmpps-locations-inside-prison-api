package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
}
