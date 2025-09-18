package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
@DiscriminatorValue("LOCATION_APPROVAL_REQUEST")
open class LocationCertificationApprovalRequest(
  id: UUID? = null,
  approvalType: ApprovalType,
  prisonId: String,
  status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,
  requestedBy: String,
  requestedDate: LocalDateTime,
  approvedOrRejectedBy: String? = null,
  approvedOrRejectedDate: LocalDateTime? = null,
  comments: String? = null,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  @JoinColumn(name = "location_id", nullable = false)
  open val location: ResidentialLocation,

  @Column(nullable = false)
  private val locationKey: String,

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

) : CertificationApprovalRequest(
  id = id,
  approvalType = approvalType,
  prisonId = prisonId,
  status = status,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  approvedOrRejectedBy = approvedOrRejectedBy,
  approvedOrRejectedDate = approvedOrRejectedDate,
  comments = comments,
) {
  override fun toDto(showLocations: Boolean) = super.toDto(showLocations).copy(
    locationKey = locationKey,
    locationId = location.id!!,
    certifiedNormalAccommodationChange = certifiedNormalAccommodationChange,
    workingCapacityChange = workingCapacityChange,
    maxCapacityChange = maxCapacityChange,
    locations = if (showLocations) {
      locations.filter { it.pathHierarchy == location.getPathHierarchy() }.map { it.toDto() }
    } else {
      null
    },
  )

  override fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    super.approve(approvedBy, approvedDate, linkedTransaction)
    location.approve(
      approvedDate = approvedDate,
      approvedBy = approvedBy,
      linkedTransaction = linkedTransaction,
    )
  }

  override fun reject(rejectedBy: String, rejectedDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    super.reject(rejectedBy, rejectedDate, linkedTransaction, comments)
    location.reject(
      rejectedDate = rejectedDate,
      rejectedBy = rejectedBy,
      linkedTransaction = linkedTransaction,
    )
  }

  override fun withdraw(withdrawnBy: String, withdrawnDate: LocalDateTime, linkedTransaction: LinkedTransaction, comments: String) {
    super.withdraw(withdrawnBy, withdrawnDate, linkedTransaction, comments)
    location.reject(
      rejectedDate = withdrawnDate,
      rejectedBy = withdrawnBy,
      linkedTransaction = linkedTransaction,
    )
  }
}
