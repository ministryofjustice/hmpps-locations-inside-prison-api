package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationalCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationalCapacityRepository

@Service
@Transactional(readOnly = true)
class SignedOperationCapacityService(
  private val prisonSignedOperationalCapacityRepository: PrisonSignedOperationalCapacityRepository,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSignedOperationalCapacity(prisonId: String): PrisonSignedOperationalCapacity? {
    // TODO need to be changed to dto
    return prisonSignedOperationalCapacityRepository.findOneByPrisonId(prisonId)
  }

  @Transactional
  fun saveSignedOperationalCapacity(prisonId: String, oc: PrisonSignedOperationalCapacity) {
    // TODO need to be changed to dto
    val opdb = prisonSignedOperationalCapacityRepository.findOneByPrisonId(prisonId) ?: PrisonSignedOperationalCapacity(
      signedOperationCapacity = oc.signedOperationCapacity,
      prisonId = prisonId,
      dateTime = oc.dateTime,
      approvedBy = oc.approvedBy,
    )
    if (opdb.id != null) {
      opdb.signedOperationCapacity = oc.signedOperationCapacity
      opdb.approvedBy = oc.approvedBy
      opdb.dateTime = oc.dateTime
    }

    val opUpdated = prisonSignedOperationalCapacityRepository.save(opdb)
    log.info("Created operational capacity [${opUpdated.id}] (Capacity=${opUpdated.signedOperationCapacity})")
    telemetryClient.trackEvent(
      "Created operational capacity",
      mapOf(
        "id" to opUpdated.id.toString(),
        "prisonId" to prisonId,
        "signedOperationCapacity" to opUpdated.signedOperationCapacity.toString(),
        "approvedBy" to opUpdated.approvedBy,
      ),
      null,
    )
  }
}
