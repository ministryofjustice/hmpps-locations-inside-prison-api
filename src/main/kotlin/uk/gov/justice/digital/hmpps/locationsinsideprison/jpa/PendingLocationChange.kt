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

  @Column(nullable = true)
  var cellMark: String? = null,

  @Column(nullable = true)
  var inCellSanitation: Boolean? = null,

) : Comparable<PendingLocationChange> {

  companion object {
    private val COMPARATOR = compareBy<PendingLocationChange>
      { it.id }
      .thenBy { it.approvalRequest }
  }

  override fun compareTo(other: PendingLocationChange) = COMPARATOR.compare(this, other)
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PendingLocationChange

    if (id != other.id) return false
    if (approvalRequest != other.approvalRequest) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + (approvalRequest?.hashCode() ?: 0)
    return result
  }
}
