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
@DiscriminatorValue("DRAFT")
open class LocationCertificationApprovalRequest(
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

  override fun getApprovalType() = ApprovalType.DRAFT

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

  override fun approve(approvedBy: String, approvedDate: LocalDateTime, linkedTransaction: LinkedTransaction) {
    super.approve(approvedBy, approvedDate, linkedTransaction)
    location.approve(
      approvedDate = approvedDate,
      approvedBy = approvedBy,
      linkedTransaction = linkedTransaction,
    )
  }
}
