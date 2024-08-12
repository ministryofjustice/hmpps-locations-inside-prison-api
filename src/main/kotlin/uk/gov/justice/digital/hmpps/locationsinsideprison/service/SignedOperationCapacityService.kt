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
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SignedOperationCapacityService(
  private val locationService: LocationService,
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
  fun saveSignedOperationalCapacity(request: SignedOperationCapacityValidRequest): SignOpCapResult {
    var newRecord = true

    val maxCap = locationService.getResidentialLocations(request.prisonId).prisonSummary?.maxCapacity ?: throw PrisonNotFoundException(request.prisonId)
    if (maxCap < request.signedOperationCapacity) {
      throw CapacityException(request.prisonId, "Signed operational capacity cannot be more than the establishment's maximum capacity", ErrorCode.SignedOpCapCannotBeMoreThanMaXCap)
    }
    val record =
      prisonSignedOperationalCapacityRepository.findOneByPrisonId(request.prisonId)?.also {
        it.signedOperationCapacity = request.signedOperationCapacity
        it.updatedBy = request.updatedBy
        it.whenUpdated = LocalDateTime.now(clock)
        newRecord = false
      } ?: prisonSignedOperationalCapacityRepository.save(
        PrisonSignedOperationCapacity(
          signedOperationCapacity = request.signedOperationCapacity,
          prisonId = request.prisonId,
          whenUpdated = LocalDateTime.now(clock),
          updatedBy = request.updatedBy,
        ),
      )
    val operation = if (newRecord) {
      "Created"
    } else {
      "Updated"
    }
    log.info("$operation operational capacity [${record.prisonId}] (Capacity=${record.signedOperationCapacity})")
    telemetryClient.trackEvent(
      "$operation operational capacity",
      mapOf(
        "id" to record.id.toString(),
        "prisonId" to record.prisonId,
        "signedOperationCapacity" to record.signedOperationCapacity.toString(),
        "updatedBy" to record.updatedBy,
      ),
      null,
    )
    return SignOpCapResult(signedOperationCapacityDto = record.toDto(), newRecord = newRecord)
  }
}

data class SignOpCapResult(
  val signedOperationCapacityDto: SignedOperationCapacityDto,
  val newRecord: Boolean,
)
