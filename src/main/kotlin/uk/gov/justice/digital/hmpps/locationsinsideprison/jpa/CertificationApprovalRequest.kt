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
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
open class CertificationApprovalRequest(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  private val prisonId: String,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  @JoinColumn(name = "location_id", nullable = false)
  open val location: ResidentialLocation,

  @Column(nullable = false)
  private val locationKey: String,

  @Column(nullable = false)
  private val requestedBy: String,

  @Column(nullable = false)
  private val requestedDate: LocalDateTime,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  open var status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,

  @Column(nullable = true)
  private var approvedOrRejectedBy: String? = null,

  @Column(nullable = true)
  private var approvedOrRejectedDate: LocalDateTime? = null,

  @Column(nullable = true)
  private var comments: String? = null,

  @Column(nullable = false)
  private var certifiedNormalAccommodationChange: Int = 0,

  @Column(nullable = false)
  private var workingCapacityChange: Int = 0,

  @Column(nullable = false)
  private var maxCapacityChange: Int = 0,

  @SortNatural
  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "certification_approval_request_id", nullable = false)
  open var locations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

) {
  fun toDto(showLocations: Boolean = false): CertificationApprovalRequestDto = CertificationApprovalRequestDto(
    id = id!!,
    locationId = location.id!!,
    locationKey = locationKey,
    prisonId = prisonId,
    status = status,
    requestedBy = requestedBy,
    requestedDate = requestedDate,
    approvedOrRejectedBy = approvedOrRejectedBy,
    approvedOrRejectedDate = approvedOrRejectedDate,
    comments = comments,
    certifiedNormalAccommodationChange = certifiedNormalAccommodationChange,
    workingCapacityChange = workingCapacityChange,
    maxCapacityChange = maxCapacityChange,
    locations = if (showLocations) {
      locations.filter { it.pathHierarchy == location.getPathHierarchy() }.map { it.toDto() }
    } else {
      null
    },
  )

  fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.APPROVED
    this.approvedOrRejectedBy = approvedBy
    this.approvedOrRejectedDate = approvedDate
    this.comments = comments
    location.approve(
      approvedDate = approvedDate,
      approvedBy = approvedBy,
      linkedTransaction = linkedTransaction,
    )
  }

  fun reject(rejectedBy: String, rejectedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.REJECTED
    this.approvedOrRejectedBy = rejectedBy
    this.approvedOrRejectedDate = rejectedDate
    this.comments = comments
    location.reject(
      rejectedDate = rejectedDate,
      rejectedBy = rejectedBy,
      linkedTransaction = linkedTransaction,
    )
  }

  fun withdraw(withdrawnBy: String, withdrawnDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    this.status = ApprovalRequestStatus.WITHDRAWN
    this.approvedOrRejectedBy = withdrawnBy
    this.approvedOrRejectedDate = withdrawnDate
    this.comments = comments
    location.reject(
      rejectedDate = withdrawnDate,
      rejectedBy = withdrawnBy,
      linkedTransaction = linkedTransaction,
    )
  }
}

enum class ApprovalRequestStatus {
  PENDING,
  APPROVED,
  REJECTED,
  WITHDRAWN,
}
