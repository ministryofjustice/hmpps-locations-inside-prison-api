package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class CertificationService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun requestApproval(locationApprovalRequest: LocationApprovalRequest): CertificationApprovalRequestDto {
    val location = residentialLocationRepository.findById(locationApprovalRequest.locationId)
      .orElseThrow { LocationNotFoundException("Location not found: ${locationApprovalRequest.locationId}") }

    if (!location.hasPendingChanges()) {
      throw ValidationException("Location must be in DRAFT or have pending changes to request approval")
    }

    if (location.isLocked()) {
      throw PendingApprovalAlreadyExistsException(location.getKey())
    }
    val username = getUsername()
    val now = LocalDateTime.now(clock)

    val linkedTransaction = LinkedTransaction(
      prisonId = location.prisonId,
      transactionType = TransactionType.REQUEST_CERTIFICATION_APPROVAL,
      transactionDetail = "Requesting approval for location ${location.getKey()}",
      transactionInvokedBy = username,
      txStartTime = now,
    )
    linkedTransactionRepository.save(linkedTransaction).also {
      log.info("Created linked transaction: $it")
    }

    val approvalRequest = location.requestApproval(requestedBy = username, requestedDate = now, linkedTransaction = linkedTransaction)
    certificationApprovalRequestRepository.save(approvalRequest)
    telemetryClient.trackEvent(
      "certification-approval-requested",
      mapOf(
        "id" to approvalRequest.id.toString(),
        "locationKey" to location.getKey(),
        "requestedBy" to username,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval requested (${approvalRequest.id}) for location ${location.getKey()} by $username")
    return CertificationApprovalRequestDto.from(approvalRequest)
  }

  fun approveCertificationRequest(approveCertificationRequest: ApproveCertificationRequestDto): CertificationApprovalRequestDto {
    val approvalRequest = certificationApprovalRequestRepository.findById(approveCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException("Approval request not found: ${approveCertificationRequest.approvalRequestReference}") }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ValidationException("Approval request is not in PENDING status")
    }

    val username = getUsername()
    val now = LocalDateTime.now(clock)

    val location = approvalRequest.location
    val linkedTransaction = LinkedTransaction(
      prisonId = location.prisonId,
      transactionType = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      transactionDetail = "Approval for approval request ${approveCertificationRequest.approvalRequestReference} for ${location.getKey()}",
      transactionInvokedBy = username,
      txStartTime = now,
    )

    approvalRequest.approve(
      approvedBy = username,
      approvedDate = now,
      linkedTransaction = linkedTransaction,
      comments = approveCertificationRequest.comments,
    )

    telemetryClient.trackEvent(
      "certification-approval-approved",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "approvedBy" to username,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval approved for location ${location.getKey()} by $username")
    return CertificationApprovalRequestDto.from(approvalRequest)
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequestDto): CertificationApprovalRequestDto {
    val approvalRequest = certificationApprovalRequestRepository.findById(rejectCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException("Approval request not found: ${rejectCertificationRequest.approvalRequestReference}") }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ValidationException("Approval request is not in PENDING status")
    }

    val now = LocalDateTime.now(clock)
    val location = approvalRequest.location
    val transactionInvokedBy = getUsername()

    val linkedTransaction = LinkedTransaction(
      prisonId = location.prisonId,
      transactionType = TransactionType.REJECT_CERTIFICATION_REQUEST,
      transactionDetail = "Rejection of approval request ${rejectCertificationRequest.approvalRequestReference} for ${location.getKey()}",
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = now,
    )

    approvalRequest.reject(
      rejectedBy = transactionInvokedBy,
      rejectedDate = now,
      linkedTransaction = linkedTransaction,
      comments = rejectCertificationRequest.comments,
    )

    telemetryClient.trackEvent(
      "certification-approval-rejected",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "rejectedBy" to transactionInvokedBy,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification rejected for location ${location.getKey()} by $transactionInvokedBy")
    return CertificationApprovalRequestDto.from(approvalRequest)
  }

  fun withdrawCertificationRequest(withdrawCertificationRequest: WithdrawCertificationRequestDto): CertificationApprovalRequestDto {
    val withdrawalRequest = certificationApprovalRequestRepository.findById(withdrawCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException("Approval request not found: ${withdrawCertificationRequest.approvalRequestReference}") }

    if (withdrawalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ValidationException("Approval request is not in PENDING status")
    }

    val now = LocalDateTime.now(clock)
    val location = withdrawalRequest.location
    val transactionInvokedBy = getUsername()

    val linkedTransaction = LinkedTransaction(
      prisonId = location.prisonId,
      transactionType = TransactionType.WITHDRAW_CERTIFICATION_REQUEST,
      transactionDetail = "Withdrawal of approval request ${withdrawCertificationRequest.approvalRequestReference} for ${location.getKey()}",
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = now,
    )

    withdrawalRequest.withdraw(
      withdrawnBy = transactionInvokedBy,
      withdrawnDate = now,
      linkedTransaction = linkedTransaction,
      comments = withdrawCertificationRequest.comments,
    )

    telemetryClient.trackEvent(
      "certification-approval-withdrawn",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "withdrawnBy" to transactionInvokedBy,
        "approvalRequestId" to withdrawalRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification withdrawn for location ${location.getKey()} by $transactionInvokedBy")
    return CertificationApprovalRequestDto.from(withdrawalRequest)
  }

  private fun getUsername() = authenticationHolder.username ?: SYSTEM_USERNAME
}

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(
  @Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,
)
