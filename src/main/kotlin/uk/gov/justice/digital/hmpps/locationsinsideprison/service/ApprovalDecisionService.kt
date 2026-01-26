package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApprovalResponse
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationCertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotInPendingStatusException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class ApprovalDecisionService(
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val cellCertificateService: CellCertificateService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun approveCertificationRequest(approveCertificationRequest: ApproveCertificationRequestDto): ApprovalResponse {
    val approvalRequest = certificationApprovalRequestRepository.findById(approveCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException(approveCertificationRequest.approvalRequestReference) }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ApprovalRequestNotInPendingStatusException(approveCertificationRequest.approvalRequestReference)
    }

    val username = getUsername()
    val now = LocalDateTime.now(clock)
    val transactionInvokedBy = getUsername()
    val approvedLocation = (approvalRequest as? LocationCertificationApprovalRequest)?.location
    val wasDraft = approvedLocation?.isDraft() ?: false

    val linkedTransaction = createLinkedTransaction(
      transactionType = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Approval for approval request ${approveCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (approvedLocation != null) "at ${approvedLocation.getKey()}" else "",
      now = now,
      transactionInvokedBy = transactionInvokedBy,
    )
    approvalRequest.approve(
      approvedBy = username,
      approvedDate = now,
      linkedTransaction = linkedTransaction,
    )

    // Create the cell certificate
    val cellCertificate = cellCertificateService.createCellCertificate(
      approvalRequest = approvalRequest,
      approvedBy = transactionInvokedBy,
      approvedDate = now,
      signedOperationCapacity = signedOperationCapacityRepository.findByPrisonId(approvalRequest.prisonId)?.signedOperationCapacity
        ?: 0,
    )

    telemetryClient.trackEvent(
      "certification-approval-approved",
      mapOf(
        "approvalType" to approvalRequest.getApprovalType().toString(),
        "approvalRequestId" to approvalRequest.id.toString(),
        "prisonId" to approvalRequest.prisonId,
        "approvedBy" to transactionInvokedBy,
        "locationId" to approvedLocation?.id.toString(),
        "locationKey" to approvedLocation?.getKey(),
      ),
      null,
    )

    val locationKeySuffix = approvedLocation?.let { "at ${it.getKey()}" } ?: ""
    log.info(
      "Certification {} of {} {} APPROVED by {}",
      cellCertificate.id,
      approvalRequest.prisonId,
      locationKeySuffix,
      transactionInvokedBy,
    )

    return ApprovalResponse(
      approvalRequest = approvalRequest.toDto(cellCertificateId = cellCertificate.id),
      prisonId = approvalRequest.prisonId,
      newLocation = wasDraft,
      location = approvedLocation?.toDto(includeChildren = true, includeParent = true),
    ).also { linkedTransaction.txEndTime = LocalDateTime.now(clock) }
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequestDto): ApprovalResponse {
    val approvalRequest = certificationApprovalRequestRepository.findById(rejectCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException(rejectCertificationRequest.approvalRequestReference) }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ApprovalRequestNotInPendingStatusException(rejectCertificationRequest.approvalRequestReference)
    }

    val now = LocalDateTime.now(clock)
    val transactionInvokedBy = getUsername()
    val location = (approvalRequest as? LocationCertificationApprovalRequest)?.location

    val linkedTransaction = createLinkedTransaction(
      transactionType = TransactionType.REJECT_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Rejection of approval request ${rejectCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (location != null) "at ${location.getKey()}" else "",
      now = now,
      transactionInvokedBy = transactionInvokedBy,
    )
    val newLocation = location?.isDraft() ?: false
    approvalRequest.reject(
      rejectedBy = transactionInvokedBy,
      rejectedDate = now,
      linkedTransaction = linkedTransaction,
      comments = rejectCertificationRequest.comments,
    )

    telemetryClient.trackEvent(
      "certification-approval-rejected",
      mapOf(
        "approvalType" to approvalRequest.getApprovalType().toString(),
        "approvalRequestId" to approvalRequest.id.toString(),
        "prisonId" to approvalRequest.prisonId,
        "locationId" to location?.id.toString(),
        "locationKey" to location?.getKey(),
        "rejectedBy" to transactionInvokedBy,
      ),
      null,
    )

    val locationKeySuffix = location?.let { "at ${it.getKey()}" } ?: ""
    log.info(
      "Certification of {} {} REJECTED by {}",
      approvalRequest.prisonId,
      locationKeySuffix,
      transactionInvokedBy,
    )
    return ApprovalResponse(
      approvalRequest = approvalRequest.toDto(),
      prisonId = approvalRequest.prisonId,
      newLocation = newLocation,
      location = location?.toDto(includeChildren = !newLocation, includeParent = !newLocation),
    ).also { linkedTransaction.txEndTime = LocalDateTime.now(clock) }
  }

  fun withdrawCertificationRequest(withdrawCertificationRequest: WithdrawCertificationRequestDto): ApprovalResponse {
    val approvalRequest = certificationApprovalRequestRepository.findById(withdrawCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException(withdrawCertificationRequest.approvalRequestReference) }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ApprovalRequestNotInPendingStatusException(withdrawCertificationRequest.approvalRequestReference)
    }

    val now = LocalDateTime.now(clock)
    val location = (approvalRequest as? LocationCertificationApprovalRequest)?.location
    val transactionInvokedBy = getUsername()
    val newLocation = location?.isDraft() ?: false

    val linkedTransaction = createLinkedTransaction(
      transactionType = TransactionType.WITHDRAW_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Withdrawal of approval request ${withdrawCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (location != null) "at ${location.getKey()}" else "",
      now = now,
      transactionInvokedBy = transactionInvokedBy,
    )

    approvalRequest.withdraw(
      withdrawnBy = transactionInvokedBy,
      withdrawnDate = now,
      linkedTransaction = linkedTransaction,
      comments = withdrawCertificationRequest.comments,
    )

    telemetryClient.trackEvent(
      "certification-approval-withdrawn",
      mapOf(
        "approvalType" to approvalRequest.getApprovalType().toString(),
        "approvalRequestId" to approvalRequest.id.toString(),
        "prisonId" to approvalRequest.prisonId,
        "locationId" to location?.id.toString(),
        "locationKey" to location?.getKey(),
        "withdrawnBy" to transactionInvokedBy,
      ),
      null,
    )

    val locationKeySuffix = location?.let { "at ${it.getKey()}" } ?: ""
    log.info(
      "Certification of {} {} WITHDRAWN by {}",
      approvalRequest.prisonId,
      locationKeySuffix,
      transactionInvokedBy,
    )

    return ApprovalResponse(
      approvalRequest = approvalRequest.toDto(),
      prisonId = approvalRequest.prisonId,
      newLocation = newLocation,
      location = location?.toDto(includeChildren = !newLocation, includeParent = !newLocation),
    ).also { linkedTransaction.txEndTime = LocalDateTime.now(clock) }
  }

  private fun createLinkedTransaction(
    transactionType: TransactionType,
    prisonId: String,
    detail: String,
    now: LocalDateTime,
    transactionInvokedBy: String,
  ): LinkedTransaction = linkedTransactionRepository.save(
    LinkedTransaction(
      prisonId = prisonId,
      transactionType = transactionType,
      transactionDetail = detail,
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = now,
    ),
  )

  private fun getUsername() = authenticationHolder.username ?: SYSTEM_USERNAME
}
