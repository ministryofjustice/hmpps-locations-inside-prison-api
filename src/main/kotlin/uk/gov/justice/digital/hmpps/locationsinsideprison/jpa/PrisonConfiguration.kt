package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequestLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.PrisonLevelApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ReactivationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonConfigurationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ServiceStatus
import java.time.LocalDateTime
import java.util.SortedSet

@Entity
class PrisonConfiguration(
  @Id
  @Column(name = "prison_id", updatable = false, nullable = false)
  val id: String,
  var resiLocationServiceActive: Boolean = false,
  var nonResiServiceActive: Boolean = false,
  var includeSegregationInRollCount: Boolean = false,
  var certificationApprovalRequired: Boolean = false,
  var whenUpdated: LocalDateTime,
  var updatedBy: String,

  @OneToMany(mappedBy = "prisonId", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  private val approvalRequests: SortedSet<PrisonLevelApprovalRequest> = sortedSetOf(),

) : Comparable<PrisonConfiguration> {

  companion object {
    private val COMPARATOR = compareBy<PrisonConfiguration>
      { it.id }
  }

  override fun compareTo(other: PrisonConfiguration) = COMPARATOR.compare(this, other)

  fun requestReactivationApproval(locationsToReactivate: List<CertificationApprovalRequestLocation>, requestedDate: LocalDateTime, requestedBy: String): ReactivationApprovalRequest = addApprovalToPrison(
    ReactivationApprovalRequest(
      prisonId = this.id,
      requestedBy = requestedBy,
      requestedDate = requestedDate,
      reasonForChange = "Reactivation requested",
      locations = locationsToReactivate.toSortedSet(),
    ),
  ) as ReactivationApprovalRequest

  fun addApprovalToPrison(approvalRequest: PrisonLevelApprovalRequest): PrisonLevelApprovalRequest {
    approvalRequests.add(approvalRequest)
    return approvalRequest
  }

  fun toPrisonConfiguration() = PrisonConfigurationDto(
    prisonId = id,
    resiLocationServiceActive = convertToStatus(resiLocationServiceActive),
    nonResiServiceActive = convertToStatus(nonResiServiceActive),
    includeSegregationInRollCount = convertToStatus(includeSegregationInRollCount),
    certificationApprovalRequired = convertToStatus(certificationApprovalRequired),
  )

  private fun convertToStatus(active: Boolean) = when (active) {
    true -> ServiceStatus.ACTIVE
    false -> ServiceStatus.INACTIVE
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as PrisonConfiguration

    return id == other.id
  }

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = "PrisonConfiguration(prisonId='$id', resiLocationServiceActive=$resiLocationServiceActive, nonResiServiceActive=$nonResiServiceActive, includeSegregationInRollCount=$includeSegregationInRollCount, certificationApprovalRequired=$certificationApprovalRequired)"
}
