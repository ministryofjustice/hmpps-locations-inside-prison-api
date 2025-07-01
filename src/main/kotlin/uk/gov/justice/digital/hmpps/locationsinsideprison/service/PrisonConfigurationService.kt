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
  fun updateResiLocationServiceActiveStatus(prisonId: String, residentialStatus: ResidentialStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val activeResi = residentialStatus == ResidentialStatus.ACTIVE
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
  fun updateCertificationApprovalProcess(prisonId: String, certificationApprovalProcessStatus: ResidentialStatus): PrisonConfigurationDto {
    val prisonConfig = activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val approvalActive = certificationApprovalProcessStatus == ResidentialStatus.ACTIVE
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

  fun getPrisonConfiguration(prisonId: String) = activePrisonService.getPrisonConfiguration(prisonId)?.toPrisonConfiguration() ?: throw PrisonNotFoundException(prisonId)
}

@Schema(description = "Prison configuration")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonConfigurationDto(
  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @Schema(description = "Indicates that the residential service is active", example = "ACTIVE", required = true)
  val resiLocationServiceActive: ResidentialStatus,
  @Schema(description = "Indicates that roll count should include segregation in its calculations for net vacancies", example = "INACTIVE", required = true)
  val includeSegregationInRollCount: ResidentialStatus,
  @Schema(description = "Indicates that this prison must go through the certification process to create or change cells", example = "INACTIVE", required = true)
  var certificationApprovalRequired: ResidentialStatus,
)

enum class ResidentialStatus {
  ACTIVE,
  INACTIVE,
}
