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

  var maxCapacity: Int = 0,
  var workingCapacity: Int = 0,
  var certifiedNormalAccommodation: Int = 0,
) {
  fun toDto() = (
    CapacityDTO(
      maxCapacity = maxCapacity,
      workingCapacity = workingCapacity,
      certifiedNormalAccommodation = certifiedNormalAccommodation,
    )
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Capacity

    if (maxCapacity != other.maxCapacity) return false
    if (workingCapacity != other.workingCapacity) return false
    if (certifiedNormalAccommodation != other.certifiedNormalAccommodation) return false

    return true
  }

  override fun hashCode(): Int {
    var result = maxCapacity
    result = 31 * result + workingCapacity
    result = 31 * result + (certifiedNormalAccommodation)
    return result
  }

  fun setCapacity(maxCapacity: Int, workingCapacity: Int, certifiedNormalAccommodation: Int) {
    this.maxCapacity = maxCapacity
    this.workingCapacity = workingCapacity
    this.certifiedNormalAccommodation = certifiedNormalAccommodation
  }
}
