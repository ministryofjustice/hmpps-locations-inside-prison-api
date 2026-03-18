package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
@DiscriminatorValue("LOCATION")
abstract class LocationCertificationApprovalRequest(
  id: UUID? = null,
  requestedBy: String,
  requestedDate: LocalDateTime,
  reasonForChange: String? = null,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], optional = false)
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
  @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "certification_approval_request_id", nullable = false)
  open var locations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

) : CertificationApprovalRequest(
  id = id,
  prisonId = location.prisonId,
  status = ApprovalRequestStatus.PENDING,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  reasonForChange = reasonForChange,
) {

  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    locationKey = locationKey,
    locationId = location.id!!,
    certifiedNormalAccommodationChange = certifiedNormalAccommodationChange,
    workingCapacityChange = workingCapacityChange,
    maxCapacityChange = maxCapacityChange,
    certificateId = cellCertificateId,
    locations = if (showLocations) {
      locations.filter { it.pathHierarchy == location.getPathHierarchy() }.map { it.toDto() }
    } else {
      null
    },
  )

  open fun updateLocations() {
    locations = sortedSetOf(location.toCertificationApprovalRequestLocation(includePending = true))
  }

  override fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction, clock: Clock) {
    super.approve(approvedBy, approvedDate, linkedTransaction, clock)
    location.approve(
      pendingApprovalRequest = this,
      approvedDate = approvedDate,
      approvedBy = approvedBy,
      linkedTransaction = linkedTransaction,
      clock = clock,
    )
  }
}
