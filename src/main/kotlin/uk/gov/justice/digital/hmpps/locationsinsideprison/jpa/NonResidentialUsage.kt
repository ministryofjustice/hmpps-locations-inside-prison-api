package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto

@Entity
class NonResidentialUsage(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  val location: Location,
  @Enumerated(EnumType.STRING)
  val usageType: NonResidentialUsageType,
  var capacity: Int? = null,
  var sequence: Int = 99,

) {
  fun toDto() =
    NonResidentialUsageDto(
      usageType = usageType,
      capacity = capacity,
      sequence = sequence,
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as NonResidentialUsage

    if (location != other.location) return false
    if (usageType != other.usageType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + usageType.hashCode()
    return result
  }
}

enum class NonResidentialUsageType(
  val description: String,
) {
  APPOINTMENT("Appointment"),
  VISIT("Visit"),
  MOVEMENT("Movement"),
  OCCURRENCE("Occurrence"),
  ADJUDICATION_HEARING("Adjudication hearing"),
  PROGRAMMES_ACTIVITIES("Programmes/activities"),
  PROPERTY("Property"),
  OTHER("Other"),
}
