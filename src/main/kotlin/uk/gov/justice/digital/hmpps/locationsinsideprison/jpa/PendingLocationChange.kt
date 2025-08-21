package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class PendingLocationChange(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(name = "approval_request_id", nullable = false)
  var approvalRequest: LocationCertificationApprovalRequest? = null,

  @Column(nullable = true)
  var maxCapacity: Int? = null,

  @Column(nullable = true)
  var certifiedNormalAccommodation: Int? = null,
)
