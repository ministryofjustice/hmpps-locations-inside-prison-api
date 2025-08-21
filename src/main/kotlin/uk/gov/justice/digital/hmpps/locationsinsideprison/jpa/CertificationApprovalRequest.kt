package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import org.hibernate.annotations.DiscriminatorFormula
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.io.Serializable
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
  val approvalType: ApprovalType,

  @Column(nullable = false)
  val prisonId: String,

  @Column(nullable = false)
  protected val requestedBy: String,

  @Column(nullable = false)
  protected val requestedDate: LocalDateTime,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  open var status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,

  @Column(nullable = true)
  protected var approvedOrRejectedBy: String? = null,

  @Column(nullable = true)
  protected var approvedOrRejectedDate: LocalDateTime? = null,

  @Column(nullable = true)
  protected var comments: String? = null,

) : Serializable {
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

  open fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.APPROVED
    this.approvedOrRejectedBy = approvedBy
    this.approvedOrRejectedDate = approvedDate
    this.comments = comments
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
