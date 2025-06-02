package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "certification_approval_request")
open class CertificationApprovalRequest(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  @JoinColumn(name = "location_id")
  val location: ResidentialLocation,

  @Column(nullable = false)
  val locationKey: String,

  @Column(nullable = false)
  val requestedBy: String,

  @Column(nullable = false)
  val requestedDate: LocalDateTime,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,

  @Column(nullable = true)
  var approvedOrRejectedBy: String? = null,

  @Column(nullable = true)
  var approvedOrRejectedDate: LocalDateTime? = null,

  @Column(nullable = true)
  var comments: String? = null,
) {
  fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    location.approve(
      approvedDate = approvedDate,
      approvedBy = approvedBy,
      linkedTransaction = linkedTransaction,
    )
    this.status = ApprovalRequestStatus.APPROVED
    this.approvedOrRejectedBy = approvedBy
    this.approvedOrRejectedDate = approvedDate
    this.comments = comments
  }

  fun reject(rejectedBy: String, rejectedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    location.reject(
      rejectedDate = rejectedDate,
      rejectedBy = rejectedBy,
      linkedTransaction = linkedTransaction,
    )

    this.status = ApprovalRequestStatus.REJECTED
    this.approvedOrRejectedBy = rejectedBy
    this.approvedOrRejectedDate = rejectedDate
    this.comments = comments
  }

  fun withdraw(withdrawnBy: String, withdrawnDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    location.reject(
      rejectedDate = withdrawnDate,
      rejectedBy = withdrawnBy,
      linkedTransaction = linkedTransaction,
    )

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
