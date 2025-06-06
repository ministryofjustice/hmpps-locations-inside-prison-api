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
  var certifiedNormalAccommodation: Int = 0,
) {
  fun toDto() = (
    CertificationDTO(
      certified = certified,
      capacityOfCertifiedCell = certifiedNormalAccommodation,
      certifiedNormalAccommodation = certifiedNormalAccommodation,
    )
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Certification

    if (certified != other.certified) return false
    if (certifiedNormalAccommodation != other.certifiedNormalAccommodation) return false

    return true
  }

  fun setCertification(certified: Boolean, certifiedNormalAccommodation: Int) {
    this.certified = certified
    this.certifiedNormalAccommodation = certifiedNormalAccommodation
  }

  override fun hashCode(): Int {
    var result = certified.hashCode()
    result = 31 * result + certifiedNormalAccommodation
    return result
  }

  override fun toString(): String = "Certification(certified=$certified, certifiedNormalAccommodation=$certifiedNormalAccommodation)"
}

fun getCertifiedSummary(certification: Certification?) = if (certification?.certified == true) {
  "Certified"
} else {
  "Uncertified"
}
