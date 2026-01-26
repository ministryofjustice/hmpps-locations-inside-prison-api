package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ApprovalDecisionService.Companion.log
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ApprovalRequestService(
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val signedOperationCapacityService: SignedOperationCapacityService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
) {

  fun getApprovalRequests(prisonId: String, status: ApprovalRequestStatus? = ApprovalRequestStatus.PENDING): List<CertificationApprovalRequestDto> {
    val requests = when {
      status != null -> certificationApprovalRequestRepository.findByPrisonIdAndStatusOrderByRequestedDateDesc(prisonId, status)
      else -> certificationApprovalRequestRepository.findByPrisonIdOrderByRequestedDateDesc(prisonId)
    }

    return requests.map { it.toDto() }
  }

  fun getApprovalRequest(id: UUID): CertificationApprovalRequestDto {
    val request = certificationApprovalRequestRepository.findById(id)
      .orElseThrow { ApprovalRequestNotFoundException(id) }

    return request.toDto(showLocations = true)
  }

  @Transactional
  fun requestDraftApproval(requestToApprove: LocationApprovalRequest): CertificationApprovalRequestDto {
    val location = residentialLocationRepository.findById(requestToApprove.locationId)
      .orElseThrow { LocationNotFoundException(requestToApprove.locationId.toString()) }

    val linkedTransaction = createLinkedTransaction(
      location.prisonId,
      "Requesting approval for location ${location.getKey()}",
    )
    val now = LocalDateTime.now(clock)
    val username = getUsername()

    val approvalRequest = certificationApprovalRequestRepository.save(location.requestApprovalForDraftLocation(requestedBy = username, requestedDate = now))

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

  @Transactional
  fun requestSignedOpCapApproval(requestToApprove: SignedOpCapApprovalRequest): CertificationApprovalRequestDto {
    val signedOpCap = signedOperationCapacityRepository.findByPrisonId(requestToApprove.prisonId)
      ?: throw LocationNotFoundException(requestToApprove.prisonId)

    if (signedOpCap.findPendingApprovalRequest() != null) {
      throw PendingApprovalAlreadyExistsException(requestToApprove.prisonId)
    }

    signedOperationCapacityService.validateSignedOpCap(requestToApprove.prisonId, requestToApprove.signedOperationalCapacity, includePendingOrDraft = true)

    val linkedTransaction = createLinkedTransaction(
      requestToApprove.prisonId,
      "Requesting approval for signed op cap change for ${requestToApprove.prisonId}",
    )
    val now = LocalDateTime.now(clock)
    val username = getUsername()

    val approvalRequest = certificationApprovalRequestRepository.save(signedOpCap.requestApproval(pendingSignedOperationCapacity = requestToApprove.signedOperationalCapacity, reasonForChange = requestToApprove.reasonForChange, requestedBy = username, requestedDate = now))

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

  private fun createLinkedTransaction(
    prisonId: String,
    detail: String,
  ): LinkedTransaction = linkedTransactionRepository.save(
    LinkedTransaction(
      prisonId = prisonId,
      transactionType = TransactionType.REQUEST_CERTIFICATION_APPROVAL,
      transactionDetail = detail,
      transactionInvokedBy = getUsername(),
      txStartTime = LocalDateTime.now(clock),
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

  @param:Schema(description = "Explanation of why the signed op cap is changing", example = "The size of the prison has changed", required = true)
  val reasonForChange: String,
)
