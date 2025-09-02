package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import java.time.LocalDateTime
import java.util.SortedSet

@Entity
class SignedOperationCapacity(

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(nullable = false, unique = true)
  val prisonId: String,
  var signedOperationCapacity: Int,

  @OneToMany(mappedBy = "signedOperationCapacity", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  private val approvalRequests: SortedSet<SignedOpCapCertificationApprovalRequest> = sortedSetOf(),

  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) {

  fun findPendingApprovalRequest(): SignedOpCapCertificationApprovalRequest? = approvalRequests.findLast { it.isPending() }

  fun requestApproval(pendingSignedOperationCapacity: Int, requestedDate: LocalDateTime, requestedBy: String): SignedOpCapCertificationApprovalRequest {
    val approvalRequest = SignedOpCapCertificationApprovalRequest(
      approvalType = ApprovalType.SIGNED_OP_CAP,
      prisonId = this.prisonId,
      requestedBy = requestedBy,
      requestedDate = requestedDate,
      signedOperationCapacityChange = pendingSignedOperationCapacity - signedOperationCapacity,
      signedOperationCapacity = this,
    )
    approvalRequests.add(approvalRequest)
    return approvalRequest
  }

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
