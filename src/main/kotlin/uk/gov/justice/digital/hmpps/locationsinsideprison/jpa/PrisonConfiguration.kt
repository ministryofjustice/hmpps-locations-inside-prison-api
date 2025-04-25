package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonConfigurationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialStatus
import java.time.LocalDateTime

@Entity
class PrisonConfiguration(
  @Id
  val prisonId: String,
  var signedOperationCapacity: Int,
  var resiLocationServiceActive: Boolean = false,
  var includeSegregationInRollCount: Boolean = false,
  var certificationApprovalRequired: Boolean = false,
  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) {
  fun toSignedOperationCapacityDto() = SignedOperationCapacityDto(
    signedOperationCapacity = signedOperationCapacity,
    prisonId = prisonId,
    whenUpdated = whenUpdated,
    updatedBy = updatedBy,
  )

  fun toPrisonConfiguration() = PrisonConfigurationDto(
    prisonId = prisonId,
    resiLocationServiceActive = convertToStatus(resiLocationServiceActive),
    includeSegregationInRollCount = convertToStatus(includeSegregationInRollCount),
    certificationApprovalRequired = convertToStatus(certificationApprovalRequired),
  )

  private fun convertToStatus(active: Boolean) = when (active) {
    true -> ResidentialStatus.ACTIVE
    false -> ResidentialStatus.INACTIVE
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as PrisonConfiguration

    return prisonId == other.prisonId
  }

  override fun hashCode(): Int = prisonId.hashCode()

  override fun toString(): String = "PrisonConfiguration(prisonId='$prisonId', signedOperationCapacity=$signedOperationCapacity, resiLocationServiceActive=$resiLocationServiceActive, includeSegregationInRollCount=$includeSegregationInRollCount, certificationApprovalRequired=$certificationApprovalRequired)"
}
