package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
abstract class PrisonLevelApprovalRequest(
  id: UUID? = null,
  prisonId: String,
  status: ApprovalRequestStatus = ApprovalRequestStatus.PENDING,
  requestedBy: String,
  requestedDate: LocalDateTime,
  approvedOrRejectedBy: String? = null,
  approvedOrRejectedDate: LocalDateTime? = null,
  reasonForChange: String? = null,
  comments: String? = null,

  @SortNatural
  @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "certification_approval_request_id", nullable = false)
  open var locations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

) : CertificationApprovalRequest(
  id = id,
  prisonId = prisonId,
  status = status,
  requestedBy = requestedBy,
  requestedDate = requestedDate,
  approvedOrRejectedBy = approvedOrRejectedBy,
  approvedOrRejectedDate = approvedOrRejectedDate,
  reasonForChange = reasonForChange,
  comments = comments,
) {

  override fun toDto(showLocations: Boolean, cellCertificateId: UUID?) = super.toDto(showLocations, cellCertificateId).copy(
    locations = if (showLocations) {
      locations.filter { it.level == 1 }.map { it.toDto() }
    } else {
      null
    },
  )
}
