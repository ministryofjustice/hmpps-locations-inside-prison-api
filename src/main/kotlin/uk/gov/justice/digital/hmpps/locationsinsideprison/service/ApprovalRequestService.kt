package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.xml.bind.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationDoesNotRequireApprovalException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReactivationDetail
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ApprovalDecisionService.Companion.log
import java.time.Clock
import java.time.LocalDateTime.now
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2

@Service
@Transactional(readOnly = true)
class ApprovalRequestService(
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val signedOperationCapacityService: SignedOperationCapacityService,
  private val activePrisonService: ActivePrisonService,
  private val sharedLocationService: SharedLocationService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
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

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      type = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      detail = "Requesting approval for location ${location.getKey()}",
    )
    val now = now(clock)
    val username = sharedLocationService.getUsername()

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
      linkedTransaction.txEndTime = now(clock)
    }
  }

  @Transactional
  fun requestReactivation(reactivationApprovalRequest: ReactivationApprovalRequest): CertificationApprovalRequestDto {
    val location = residentialLocationRepository.findById(reactivationApprovalRequest.locationId)
      .orElseThrow { LocationNotFoundException(reactivationApprovalRequest.locationId.toString()) }

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      type = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      detail = "Requesting reactivation approval for location ${location.getKey()}",
    )
    val now = now(clock)
    val username = sharedLocationService.getUsername()

    val approvalRequired = activePrisonService.isCertificationApprovalRequired(location.prisonId)
    if (!approvalRequired) {
      throw LocationDoesNotRequireApprovalException("Certification approval not required for location ${location.getKey()}")
    }
    val locations = reactivationApprovalRequest.locations.map { (id, details) ->
      val locationToUpdate = residentialLocationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (locationToUpdate.isPermanentlyDeactivated()) {
        throw LocationCannotBeReactivatedException("Location [${locationToUpdate.getKey()}] permanently deactivated")
      }

      if (locationToUpdate.hasPendingCertificationApproval()) {
        throw PendingApprovalAlreadyExistsException(locationToUpdate.getKey())
      }

      if (!locationToUpdate.isInHierarchy(location)) {
        throw ValidationException("Location [${locationToUpdate.getKey()}] is not a child of location [${location.getKey()}]")
      }

      locationToUpdate.toCertificationApprovalRequestLocation(
        cascadeReactivation = details.cascadeReactivation,
        specialistCellTypeAsCSV = details.getSpecialistCellTypesAsCSV(),
        capacity = details.capacity,
      )
    }

    val approvalRequest = certificationApprovalRequestRepository.save(
      location.requestReactivationApproval(locationsToReactivate = locations, requestedBy = username, requestedDate = now),
    )

    telemetryClient.trackEvent(
      "reactivation-approval-requested",
      mapOf(
        "id" to approvalRequest.id.toString(),
        "locationKey" to location.getKey(),
        "requestedBy" to username,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Reactivation approval requested (${approvalRequest.id}) for location ${location.getKey()} by $username")
    return approvalRequest.toDto(showLocations = true).also {
      linkedTransaction.txEndTime = now(clock)
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

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      prisonId = requestToApprove.prisonId,
      detail = "Requesting approval for signed op cap change for ${requestToApprove.prisonId}",
      type = TransactionType.APPROVE_CERTIFICATION_REQUEST,
    )
    val now = now(clock)
    val username = sharedLocationService.getUsername()

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
      linkedTransaction.txEndTime = now(clock)
    }
  }
}

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(
  @param:Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,
)

@Schema(description = "Reactivate locations Approval Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReactivationApprovalRequest(
  @param:Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,
  @param:Schema(description = "List of locations below the locationId to reactivate", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"cascadeReactivation\": false, \"capacity\": { \"workingCapacity\": 1, \"maxCapacity\": 2 } } }")
  val locations: Map<UUID, ReactivationDetail>,
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
