package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

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
class CertificationApprovalRequest(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "location_id")
  val location: Location,

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
  fun approve(approvedBy: String, comments: String?, approvedDate: LocalDateTime) {
    this.status = ApprovalRequestStatus.APPROVED
    this.approvedOrRejectedBy = approvedBy
    this.approvedOrRejectedDate = approvedDate
    this.comments = comments
  }

  fun reject(rejectedBy: String, comments: String?, rejectedDate: LocalDateTime) {
    this.status = ApprovalRequestStatus.REJECTED
    this.approvedOrRejectedBy = rejectedBy
    this.approvedOrRejectedDate = rejectedDate
    this.comments = comments
  }
}

enum class ApprovalRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
}
