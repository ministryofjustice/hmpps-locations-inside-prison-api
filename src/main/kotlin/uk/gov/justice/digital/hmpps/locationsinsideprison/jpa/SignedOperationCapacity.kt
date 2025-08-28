package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import java.time.LocalDateTime

@Entity
class SignedOperationCapacity(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  val prisonId: String,
  var signedOperationCapacity: Int,

  var pendingSignedOperationCapacity: Int? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = true)
  @JoinColumn(name = "certification_approval_request_id", nullable = true)
  var approvalRequest: SignedOpCapCertificationApprovalRequest? = null,

  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) {

  fun toSignedOperationCapacityDto() = SignedOperationCapacityDto(
    signedOperationCapacity = signedOperationCapacity,
    prisonId = prisonId,
    whenUpdated = whenUpdated,
    updatedBy = updatedBy,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as SignedOperationCapacity

    return prisonId == other.prisonId
  }

  override fun hashCode(): Int = prisonId.hashCode()
}
