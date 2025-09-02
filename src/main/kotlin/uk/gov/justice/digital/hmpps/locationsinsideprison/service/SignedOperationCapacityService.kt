package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityValidRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SignedOperationCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CapacityException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ErrorCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.SignedOpCapCannotChangedWithoutApprovalException
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class SignedOperationCapacityService(
  private val locationService: LocationService,
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val prisonConfigurationService: PrisonConfigurationService,
  private val telemetryClient: TelemetryClient,
  private val clock: Clock,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSignedOperationalCapacity(prisonId: String): SignedOperationCapacityDto? = signedOperationCapacityRepository.findByPrisonId(prisonId)?.toSignedOperationCapacityDto()

  @Transactional
  fun saveSignedOperationalCapacity(request: SignedOperationCapacityValidRequest): SignOpCapResult {
    val prisonConfiguration = prisonConfigurationService.getPrisonConfiguration(request.prisonId)
    if (prisonConfiguration.certificationApprovalRequired == ResidentialStatus.ACTIVE) {
      throw SignedOpCapCannotChangedWithoutApprovalException(prisonConfiguration.prisonId)
    }

    var newRecord = true

    val tx = createLinkedTransaction(prisonId = request.prisonId, TransactionType.SIGNED_OP_CAP, "Signed op cap for prison ${request.prisonId} set to ${request.signedOperationCapacity}", request.updatedBy)

    validateSignedOpCap(request.prisonId, request.signedOperationCapacity)
    val record =
      signedOperationCapacityRepository.findByPrisonId(request.prisonId)?.also {
        it.signedOperationCapacity = request.signedOperationCapacity
        it.updatedBy = request.updatedBy
        it.whenUpdated = LocalDateTime.now(clock)
        newRecord = false
      } ?: signedOperationCapacityRepository.save(
        SignedOperationCapacity(
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
        "prisonId" to record.prisonId,
        "signedOperationCapacity" to record.signedOperationCapacity.toString(),
        "updatedBy" to record.updatedBy,
      ),
      null,
    )
    return SignOpCapResult(signedOperationCapacityDto = record.toSignedOperationCapacityDto(), newRecord = newRecord).also {
      tx.txEndTime = LocalDateTime.now(clock)
    }
  }

  fun validateSignedOpCap(prisonId: String, signedOperationCapacity: Int) {
    val maxCap = locationService.getResidentialLocations(prisonId).prisonSummary?.maxCapacity
      ?: throw PrisonNotFoundException(prisonId)
    if (maxCap < signedOperationCapacity) {
      throw CapacityException(
        prisonId,
        "Signed operational capacity cannot be more than the establishment's maximum capacity of $maxCap",
        ErrorCode.SignedOpCapCannotBeMoreThanMaxCap,
      )
    }
  }

  private fun createLinkedTransaction(prisonId: String, type: TransactionType, detail: String, transactionInvokedBy: String): LinkedTransaction {
    val linkedTransaction = LinkedTransaction(
      prisonId = prisonId,
      transactionType = type,
      transactionDetail = detail,
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = LocalDateTime.now(clock),
    )
    return linkedTransactionRepository.save(linkedTransaction).also {
      LocationService.log.info("Created linked transaction: $it")
    }
  }
}

data class SignOpCapResult(
  val signedOperationCapacityDto: SignedOperationCapacityDto,
  val newRecord: Boolean,
)
