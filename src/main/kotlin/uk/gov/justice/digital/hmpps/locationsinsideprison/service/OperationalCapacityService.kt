package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.OperationalCapacityRepository

@Service
@Transactional(readOnly = true)
class OperationalCapacityService(
  private val operationalCapacityRepository: OperationalCapacityRepository,
  private val telemetryClient: TelemetryClient,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getOperationalCapacity(prisonId: String): OperationalCapacity? {
    //TODO need to be changed to dto
    return operationalCapacityRepository.findOneByPrisonId(prisonId)
  }

  @Transactional
  fun saveOperationalCapacity(prisonId: String, oc: OperationalCapacity) {
    //TODO need to be changed to dto
    val opdb = operationalCapacityRepository.findOneByPrisonId(prisonId) ?: OperationalCapacity(
      capacity = oc.capacity,
      prisonId = prisonId,
      dateTime = oc.dateTime,
      approvedBy = oc.approvedBy,
    )
    if (opdb.id != null) {
      opdb.capacity = oc.capacity
      opdb.approvedBy = oc.approvedBy
      opdb.dateTime = oc.dateTime
    }

    val opUpdated = operationalCapacityRepository.save(opdb)
    log.info("Created operational capacity [${opUpdated.id}] (Capacity=${opUpdated.capacity})")
    telemetryClient.trackEvent(
      "Created operational capacity",
      mapOf(
        "id" to opUpdated.id.toString(),
        "prisonId" to prisonId,
        "capacity" to opUpdated.capacity.toString(),
        "approvedBy" to opUpdated.approvedBy,
      ),
      null,
    )
  }
}
