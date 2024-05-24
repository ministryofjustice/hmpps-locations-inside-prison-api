package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityValidRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SignedOperationCapacityService(
  private val prisonSignedOperationalCapacityRepository: PrisonSignedOperationCapacityRepository,
  private val telemetryClient: TelemetryClient,
  private val clock: Clock,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSignedOperationalCapacity(prisonId: String): SignedOperationCapacityDto? {
    return prisonSignedOperationalCapacityRepository.findOneByPrisonId(prisonId)?.toDto()
  }

  @Transactional
  fun saveSignedOperationalCapacity(request: SignedOperationCapacityValidRequest): PrisonSignedOperationCapacity {
    val record =
      prisonSignedOperationalCapacityRepository.findOneByPrisonId(request.prisonId) ?: PrisonSignedOperationCapacity(
        signedOperationCapacity = request.signedOperationCapacity,
        prisonId = request.prisonId,
        whenUpdated = LocalDateTime.now(clock),
        updatedBy = request.updatedBy,
      )
    if (record.id != null) {
      record.signedOperationCapacity = request.signedOperationCapacity
      record.updatedBy = request.updatedBy
      record.whenUpdated = LocalDateTime.now(clock)
    }

    val persistedRecord = prisonSignedOperationalCapacityRepository.save(record)
    log.info("Created operational capacity [${persistedRecord.id}] (Capacity=${persistedRecord.signedOperationCapacity})")
    telemetryClient.trackEvent(
      "Created operational capacity",
      mapOf(
        "id" to persistedRecord.id.toString(),
        "prisonId" to request.prisonId,
        "signedOperationCapacity" to persistedRecord.signedOperationCapacity.toString(),
        "updatedBy" to persistedRecord.updatedBy,
      ),
      null,
    )
    return persistedRecord
  }
}
