package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonConfigurationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ServiceStatus
import java.time.LocalDateTime

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
) : Comparable<PrisonConfiguration> {

  companion object {
    private val COMPARATOR = compareBy<PrisonConfiguration>
      { it.id }
  }

  override fun compareTo(other: PrisonConfiguration) = COMPARATOR.compare(this, other)

  fun isCertificationActive() = certificationApprovalRequired

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
