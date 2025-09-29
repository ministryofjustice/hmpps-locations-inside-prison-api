package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import org.hibernate.Hibernate
import org.hibernate.annotations.DiscriminatorFormula
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.UUID

@Entity
@DiscriminatorFormula("CASE WHEN approval_type = 'SIGNED_OP_CAP' THEN 'SIGNED_OP_CAP_APPROVAL_REQUEST' ELSE 'LOCATION_APPROVAL_REQUEST' END")
@Table(name = "certification_approval_request")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class CertificationApprovalRequest(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Enumerated(EnumType.STRING)
  open val approvalType: ApprovalType,

  @Column(nullable = false)
  open val prisonId: String,

  @Column(nullable = false)
  open val requestedBy: String,

  @Column(nullable = false)
  open val requestedDate: LocalDateTime,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  open var status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,

  @Column(nullable = true)
  open var approvedOrRejectedBy: String? = null,

  @Column(nullable = true)
  open var approvedOrRejectedDate: LocalDateTime? = null,

  @Column(nullable = true)
  open var comments: String? = null,

) : Comparable<CertificationApprovalRequest> {

  companion object {
    private val COMPARATOR = compareBy<CertificationApprovalRequest>
      { it.prisonId }
      .thenBy { it.approvalType }
      .thenBy { it.requestedDate }
      .thenBy { it.status }
  }

  override fun compareTo(other: CertificationApprovalRequest) = COMPARATOR.compare(this, other)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as CertificationApprovalRequest

    if (prisonId != other.prisonId) return false
    if (approvalType != other.approvalType) return false
    if (!requestedDate.isEqual(other.requestedDate)) return false
    if (status != other.status) return false

    return true
  }

  override fun hashCode(): Int {
    var result = prisonId.hashCode()
    result = 31 * result + approvalType.hashCode()
    result = 31 * result + requestedDate.hashCode()
    result = 31 * result + status.hashCode()
    return result
  }

  fun isPending() = status == ApprovalRequestStatus.PENDING

  open fun toDto(showLocations: Boolean = false): CertificationApprovalRequestDto = CertificationApprovalRequestDto(
    id = id!!,
    approvalType = approvalType,
    prisonId = prisonId,
    status = status,
    requestedBy = requestedBy,
    requestedDate = requestedDate,
    approvedOrRejectedBy = approvedOrRejectedBy,
    approvedOrRejectedDate = approvedOrRejectedDate,
    comments = comments,
  )

  open fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    this.status = ApprovalRequestStatus.APPROVED
    this.approvedOrRejectedBy = approvedBy
    this.approvedOrRejectedDate = approvedDate
  }

  open fun reject(rejectedBy: String, rejectedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.REJECTED
    this.approvedOrRejectedBy = rejectedBy
    this.approvedOrRejectedDate = rejectedDate
    this.comments = comments
  }

  open fun withdraw(withdrawnBy: String, withdrawnDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.WITHDRAWN
    this.approvedOrRejectedBy = withdrawnBy
    this.approvedOrRejectedDate = withdrawnDate
    this.comments = comments
  }
}

enum class ApprovalRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
  WITHDRAWN,
}

enum class ApprovalType {
  SIGNED_OP_CAP,
  DRAFT,
  DEACTIVATION,
  REACTIVATION,
  CAPACITY_CHANGE,
}
