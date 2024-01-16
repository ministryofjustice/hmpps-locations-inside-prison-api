package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Certification(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  var certified: Boolean = false,
  var capacityOfCertifiedCell: Int = 0,
)
