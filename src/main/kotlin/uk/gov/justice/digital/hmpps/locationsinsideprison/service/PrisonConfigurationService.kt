package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PrisonConfigurationService(
  private val activePrisonService: ActivePrisonService,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun updateResiLocationServiceActiveStatus(prisonId: String, residentialStatus: ServiceStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val activeResi = residentialStatus == ServiceStatus.ACTIVE
    if (prisonConfig.resiLocationServiceActive != activeResi) {
      val tx = LinkedTransaction(
        transactionType = TransactionType.RESI_SERVICE_ACTIVATION,
        prisonId = prisonId,
        transactionDetail = "Residential service status changed to $residentialStatus",
        transactionInvokedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      )
      linkedTransactionRepository.save(tx)
      activePrisonService.setResiLocationServiceActive(prisonId, activeResi)

      telemetryClient.trackEvent(
        "Residential service configuration update",
        mapOf(
          "prisonId" to prisonId,
          "residentialStatus" to residentialStatus.name,
          "tx" to tx.transactionId.toString(),
        ),
        null,
      )
      log.info("Updated residential service status [$tx]")
      tx.txEndTime = LocalDateTime.now(clock)
    } else {
      log.warn("No change applied to resi service already is $residentialStatus")
    }

    return prisonConfig.toPrisonConfiguration()
  }

  @Transactional
  fun updateNonResiLocationServiceActiveStatus(prisonId: String, nonResiStatus: ServiceStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val activeNonResi = nonResiStatus == ServiceStatus.ACTIVE
    if (prisonConfig.nonResiServiceActive != activeNonResi) {
      val tx = LinkedTransaction(
        transactionType = TransactionType.NON_RESI_SERVICE_ACTIVATION,
        prisonId = prisonId,
        transactionDetail = "Non-resi service status changed to $nonResiStatus",
        transactionInvokedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      )
      linkedTransactionRepository.save(tx)
      activePrisonService.setNonResiServiceActive(prisonId, activeNonResi)

      telemetryClient.trackEvent(
        "Residential service configuration update",
        mapOf(
          "prisonId" to prisonId,
          "nonResiStatus" to nonResiStatus.name,
          "tx" to tx.transactionId.toString(),
        ),
        null,
      )
      log.info("Updated non-resi service status [$tx]")
      tx.txEndTime = LocalDateTime.now(clock)
    } else {
      log.warn("No change applied to non-resi service already is $nonResiStatus")
    }

    return prisonConfig.toPrisonConfiguration()
  }

  @Transactional
  fun updateCertificationApprovalProcess(prisonId: String, certificationApprovalProcessStatus: ServiceStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val approvalActive = certificationApprovalProcessStatus == ServiceStatus.ACTIVE
    if (prisonConfig.certificationApprovalRequired != approvalActive) {
      val tx = LinkedTransaction(
        transactionType = TransactionType.APPROVAL_PROCESS_ACTIVATION,
        prisonId = prisonId,
        transactionDetail = "Certification approval process changed to $certificationApprovalProcessStatus",
        transactionInvokedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      )
      linkedTransactionRepository.save(tx)
      prisonConfig.certificationApprovalRequired = approvalActive

      telemetryClient.trackEvent(
        "Certification approval process configuration update",
        mapOf(
          "prisonId" to prisonId,
          "certificationApprovalProcess" to certificationApprovalProcessStatus.name,
          "tx" to tx.transactionId.toString(),
        ),
        null,
      )
      log.info("Updated certification approval status [$tx]")
      tx.txEndTime = LocalDateTime.now(clock)
    } else {
      log.warn("No change applied approval process, service already is $certificationApprovalProcessStatus")
    }

    return prisonConfig.toPrisonConfiguration()
  }

  @Transactional
  fun updateIncludeSegInRollCount(prisonId: String, includeSegInRollCountStatus: ServiceStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val includeSegActive = includeSegInRollCountStatus == ServiceStatus.ACTIVE
    if (prisonConfig.includeSegregationInRollCount != includeSegActive) {
      val tx = LinkedTransaction(
        transactionType = TransactionType.INCLUDE_SEG_IN_ROLL_COUNT_ACTIVATION,
        prisonId = prisonId,
        transactionDetail = "Include seg in roll count changed to $includeSegInRollCountStatus",
        transactionInvokedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      )
      linkedTransactionRepository.save(tx)
      prisonConfig.includeSegregationInRollCount = includeSegActive

      telemetryClient.trackEvent(
        "Include seg in roll count update",
        mapOf(
          "prisonId" to prisonId,
          "includeSegInRollCount" to includeSegInRollCountStatus.name,
          "tx" to tx.transactionId.toString(),
        ),
        null,
      )
      log.info("Include seg in roll count update [$tx]")
      tx.txEndTime = LocalDateTime.now(clock)
    } else {
      log.warn("No change applied, include seg in roll count already is $includeSegInRollCountStatus")
    }

    return prisonConfig.toPrisonConfiguration()
  }

  fun getPrisonConfiguration(prisonId: String) = activePrisonService.getPrisonConfiguration(prisonId)?.toPrisonConfiguration() ?: throw PrisonNotFoundException(prisonId)
}

@Schema(description = "Prison configuration")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonConfigurationDto(
  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @param:Schema(description = "Indicates that the residential service is active", example = "ACTIVE", required = true)
  val resiLocationServiceActive: ServiceStatus,
  @param:Schema(description = "Indicates that the non-resi service is active", example = "ACTIVE", required = true)
  val nonResiServiceActive: ServiceStatus,
  @param:Schema(description = "Indicates that roll count should include segregation in its calculations for net vacancies", example = "INACTIVE", required = true)
  val includeSegregationInRollCount: ServiceStatus,
  @param:Schema(description = "Indicates that this prison must go through the certification process to create or change cells", example = "INACTIVE", required = true)
  var certificationApprovalRequired: ServiceStatus,
)

enum class ServiceStatus {
  ACTIVE,
  INACTIVE,
}
