package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApprovalResponse
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationCertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotInPendingStatusException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationDoesNotRequireApprovalException
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
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val signedOperationCapacityService: SignedOperationCapacityService,
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

  fun requestApproval(requestToApprove: LocationApprovalRequest): CertificationApprovalRequestDto {
    val location = residentialLocationRepository.findById(requestToApprove.locationId)
      .orElseThrow { LocationNotFoundException(requestToApprove.locationId.toString()) }

    if (!location.hasPendingChanges()) {
      throw LocationDoesNotRequireApprovalException(location.getKey())
    }

    if (location.isLocationLocked()) {
      throw PendingApprovalAlreadyExistsException(location.getKey())
    }
    val username = getUsername()
    val now = LocalDateTime.now(clock)

    val linkedTransaction = createLinkedTransaction(
      TransactionType.REQUEST_CERTIFICATION_APPROVAL,
      location.prisonId,
      "Requesting approval for location ${location.getKey()}",
      now,
      username,
    )
    val approvalRequest = certificationApprovalRequestRepository.save(location.requestApproval(requestedBy = username, requestedDate = now, linkedTransaction = linkedTransaction))

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
    return approvalRequest.toDto(showLocations = true).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  fun requestApproval(requestToApprove: SignedOpCapApprovalRequest): CertificationApprovalRequestDto {
    val signedOpCap = signedOperationCapacityRepository.findByPrisonId(requestToApprove.prisonId)
      ?: throw LocationNotFoundException(requestToApprove.prisonId)

    if (signedOpCap.findPendingApprovalRequest() != null) {
      throw PendingApprovalAlreadyExistsException(requestToApprove.prisonId)
    }

    signedOperationCapacityService.validateSignedOpCap(requestToApprove.prisonId, requestToApprove.signedOperationalCapacity)

    val username = getUsername()
    val now = LocalDateTime.now(clock)

    val linkedTransaction = createLinkedTransaction(
      TransactionType.REQUEST_CERTIFICATION_APPROVAL,
      requestToApprove.prisonId,
      "Requesting approval for signed op cap change for ${requestToApprove.prisonId}",
      now,
      username,
    )
    val approvalRequest = certificationApprovalRequestRepository.save(signedOpCap.requestApproval(pendingSignedOperationCapacity = requestToApprove.signedOperationalCapacity, requestedBy = username, requestedDate = now))

    telemetryClient.trackEvent(
      "certification-op-cap-approval-requested",
      mapOf(
        "id" to approvalRequest.id.toString(),
        "prisonId" to requestToApprove.prisonId,
        "requestedBy" to username,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval requested for Op-Cap change (${approvalRequest.id}) for prison ${requestToApprove.prisonId} by $username")
    return approvalRequest.toDto(showLocations = true).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
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
      comments = approveCertificationRequest.comments,
    )

    // Create the cell certificate
    cellCertificateService.createCellCertificate(
      approvalRequest = approvalRequest,
      approvedBy = transactionInvokedBy,
      approvedDate = now,
      approvedLocation = approvedLocation,
    )

    telemetryClient.trackEvent(
      "certification-approval-approved",
      mapOf(
        "approvalType" to approvalRequest.approvalType.toString(),
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
      "Certification approval approved for {} {} by {}",
      approvalRequest.prisonId,
      locationKeySuffix,
      transactionInvokedBy,
    )

    return ApprovalResponse(
      approvalRequest = approvalRequest.toDto(),
      prisonId = approvalRequest.prisonId,
      newLocation = approvedLocation?.isDraft() ?: false,
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
        "approvalType" to approvalRequest.approvalType.toString(),
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
      "Certification rejected for {} {} by {}",
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
        "approvalType" to approvalRequest.approvalType.toString(),
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
      "Certification withdrawn for {} {} by {}",
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

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(
  @param:Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,

  @param:Schema(description = "Type of approval request", example = "DRAFT", required = true)
  val approvalType: ApprovalType = ApprovalType.DRAFT,
)

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignedOpCapApprovalRequest(
  @param:Schema(description = "The prison where the signed op cap is to be approved", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(description = "The new value of the signed operational capacity", example = "456", required = true)
  val signedOperationalCapacity: Int,
)
