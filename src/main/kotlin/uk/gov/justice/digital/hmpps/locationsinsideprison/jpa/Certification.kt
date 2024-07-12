package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification as CertificationDTO

@Entity
class Certification(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  var certified: Boolean = false,
  var capacityOfCertifiedCell: Int = 0,
) {
  fun toDto() = (
    CertificationDTO(
      certified = certified,
      capacityOfCertifiedCell = capacityOfCertifiedCell,
    )
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Certification

    if (certified != other.certified) return false
    if (capacityOfCertifiedCell != other.capacityOfCertifiedCell) return false

    return true
  }

  fun setCertification(certified: Boolean, capacityOfCertifiedCell: Int) {
    this.certified = certified
    this.capacityOfCertifiedCell = capacityOfCertifiedCell
  }

  override fun hashCode(): Int {
    var result = certified.hashCode()
    result = 31 * result + capacityOfCertifiedCell
    return result
  }

  override fun toString(): String {
    return "Certification(certified=$certified, capacityOfCertifiedCell=$capacityOfCertifiedCell)"
  }
}
