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

    val opdb = prisonSignedOperationalCapacityRepository.findOneByPrisonId(request.prisonId) ?: PrisonSignedOperationCapacity(
      signedOperationCapacity = request.signedOperationCapacity,
      prisonId = request.prisonId,
      dateTime = LocalDateTime.now(clock),
      updatedBy = request.updatedBy,
    )
    if (opdb.id != null) {
      opdb.signedOperationCapacity = request.signedOperationCapacity
      opdb.updatedBy = request.updatedBy
      opdb.dateTime = LocalDateTime.now(clock)
    }

    val opUpdated = prisonSignedOperationalCapacityRepository.save(opdb)
    log.info("Created operational capacity [${opUpdated.id}] (Capacity=${opUpdated.signedOperationCapacity})")
    telemetryClient.trackEvent(
      "Created operational capacity",
      mapOf(
        "id" to opUpdated.id.toString(),
        "prisonId" to request.prisonId,
        "signedOperationCapacity" to opUpdated.signedOperationCapacity.toString(),
        "updatedBy" to opUpdated.updatedBy,
      ),
      null,
    )
    return opUpdated
  }
}
