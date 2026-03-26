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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ReactivationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellReactivationDetail
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationDoesNotRequireApprovalException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalAlreadyExistsException
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
  private val cellCertificateRepository: CellCertificateRepository,
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
  fun requestReactivationApproval(reactivationApprovalRequest: ReactivationLocationsApprovalRequest): CertificationApprovalRequestDto {
    val topLevelLocation = residentialLocationRepository.findById(reactivationApprovalRequest.topLevelLocationId)
      .orElseThrow { LocationNotFoundException(reactivationApprovalRequest.topLevelLocationId.toString()) }

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      prisonId = topLevelLocation.prisonId,
      type = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      detail = "Requesting reactivation approval for location ${topLevelLocation.getKey()}",
    )

    val approvalRequired = activePrisonService.isCertificationApprovalRequired(topLevelLocation.prisonId)
    if (!approvalRequired) {
      throw LocationDoesNotRequireApprovalException("Certification approval not required for location ${topLevelLocation.getKey()}")
    }
    if (reactivationApprovalRequest.cascadeReactivation && reactivationApprovalRequest.cellReactivationChanges != null) {
      throw ValidationException("Cannot specify both cascadeReactivation and cellReactivationChanges")
    }

    if (topLevelLocation.hasPendingCertificationApproval()) {
      throw PendingApprovalAlreadyExistsException(topLevelLocation.getKey())
    }

    val approvalRequest = ReactivationApprovalRequest(
      location = topLevelLocation,
      requestedBy = linkedTransaction.transactionInvokedBy,
      requestedDate = linkedTransaction.txStartTime,
    )
    topLevelLocation.addApprovalToLocation(approvalRequest)
    certificationApprovalRequestRepository.save(approvalRequest)

    // Update the current capacities from the certificate if there is one.
    val locationHierarchy = approvalRequest.getTopLevelLocation() ?: throw LocationNotFoundException("No top level location")
    locationHierarchy.findSubLocations().forEach { subLocation ->
      cellCertificateRepository.findByPrisonIdAndPathHierarchy(topLevelLocation.prisonId, subLocation.pathHierarchy)?.let { currentCellCert ->
        subLocation.currentWorkingCapacity = currentCellCert.workingCapacity
        subLocation.currentMaxCapacity = currentCellCert.maxCapacity
        subLocation.currentCertifiedNormalAccommodation = currentCellCert.certifiedNormalAccommodation
      }
      if (reactivationApprovalRequest.cascadeReactivation && subLocation.findSubLocations().isEmpty()) {
        subLocation.reactivateThisLocation = true
      }
    }

    // Go through the cellReactivationChanges and update the certificationChanges to reflect the capacity and specialist cell types held in the cellReactivationChanges
    reactivationApprovalRequest.cellReactivationChanges?.map { (id, details) ->
      val cellToUpdate = residentialLocationRepository.findById(id)
        .orElseThrow { LocationNotFoundException(id.toString()) }

      if (cellToUpdate.isPermanentlyDeactivated()) {
        throw LocationCannotBeReactivatedException("Location [${cellToUpdate.getKey()}] permanently deactivated")
      }

      if (!cellToUpdate.isInHierarchy(topLevelLocation)) {
        throw ValidationException("Location [${cellToUpdate.getKey()}] is not a child of location [${topLevelLocation.getKey()}]")
      }

      locationHierarchy.findLocationByPathHierarchy(cellToUpdate.getPathHierarchy())?.let { approvalCell ->
        approvalCell.reactivateThisLocation = true
        details.capacity?.let {
          approvalCell.workingCapacity = it.workingCapacity
          approvalCell.maxCapacity = it.maxCapacity
          approvalCell.certifiedNormalAccommodation = it.certifiedNormalAccommodation
        }
        details.getSpecialistCellTypesAsCSV()?.let {
          approvalCell.specialistCellTypes = it
        }
      }
    }

    approvalRequest.refreshCapacities()

    telemetryClient.trackEvent(
      "reactivation-approval-requested",
      mapOf(
        "id" to approvalRequest.id.toString(),
        "locationKey" to topLevelLocation.getKey(),
        "requestedBy" to linkedTransaction.transactionInvokedBy,
        "approvalRequestId" to approvalRequest.id.toString(),
      ),
      null,
    )

    log.info("Reactivation approval requested (${approvalRequest.id}) for location ${topLevelLocation.getKey()} by ${linkedTransaction.transactionInvokedBy}")
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
data class ReactivationLocationsApprovalRequest(
  @param:Schema(description = "The top level location Id of location for reactivation and requiring approval", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val topLevelLocationId: UUID,
  @param:Schema(description = "Cascade the reactivation from the top level, cells will be reactivated in their previous state, if this is true `cellReactivationChanges` should be null", defaultValue = "false", required = false, example = "true")
  val cascadeReactivation: Boolean = false,
  @param:Schema(description = "List of cells below the locationId to reactivate, with capacity and ttype details, missing cells will not be reactivated", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"cascadeReactivation\": false, \"capacity\": { \"workingCapacity\": 1, \"maxCapacity\": 2 } } }")
  val cellReactivationChanges: Map<UUID, CellReactivationDetail>? = null,
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
