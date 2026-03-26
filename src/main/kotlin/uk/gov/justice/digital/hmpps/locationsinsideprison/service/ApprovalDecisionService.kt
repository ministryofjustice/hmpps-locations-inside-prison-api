package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApprovalResponse
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequestLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.LocationCertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ReactivationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotInPendingStatusException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ReactivationDetail
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO
@Service
@Transactional
class ApprovalDecisionService(
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val cellCertificateService: CellCertificateService,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val sharedLocationService: SharedLocationService,
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

    val username = sharedLocationService.getUsername()
    val now = LocalDateTime.now(clock)
    val transactionInvokedBy = sharedLocationService.getUsername()
    val approvedLocation = (approvalRequest as? LocationCertificationApprovalRequest)?.location
    val wasDraft = approvedLocation?.isDraft() ?: false

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      type = TransactionType.APPROVE_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Approval for approval request ${approveCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (approvedLocation != null) "at ${approvedLocation.getKey()}" else "",
      transactionInvokedBy = transactionInvokedBy,
    )

    val events = if (approvalRequest is ReactivationApprovalRequest) {
      handleReactivation(approvalRequest, linkedTransaction)
    } else {
      null
    }

    approvalRequest.approve(
      approvedBy = username,
      approvedDate = now,
      linkedTransaction = linkedTransaction,
      clock = clock,
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
      location = if (events != null) null else approvedLocation?.toDto(includeChildren = true, includeParent = true),
      events = events,
    ).also { linkedTransaction.txEndTime = LocalDateTime.now(clock) }
  }

  private fun handleReactivation(
    approvalRequest: ReactivationApprovalRequest,
    linkedTransaction: LinkedTransaction,
  ): Map<InternalLocationDomainEventType, List<LocationDTO>> {
    val locationsReactivated = mutableSetOf<Location>()
    val amendedLocations = mutableSetOf<Location>()

    approvalRequest.getTopLevelLocation()?.findAllLeafLocations()?.forEach { approvalChangeLocation ->
      val locationToReactivate = residentialLocationRepository.findOneByPrisonIdAndPathHierarchy(
        approvalRequest.prisonId,
        approvalChangeLocation.pathHierarchy,
      ) ?: throw LocationNotFoundException(
        "Location not found for prison ${approvalRequest.prisonId} and path hierarchy ${approvalChangeLocation.pathHierarchy}",
      )

      buildReactivationDetail(approvalChangeLocation)?.let { reactivationDetail ->
        sharedLocationService.reactivate(
          locationToReactivate = locationToReactivate,
          locationsReactivated = locationsReactivated,
          amendedLocations = amendedLocations,
          reactivationDetail = reactivationDetail,
          linkedTransaction = linkedTransaction,
        )
      }
    }

    locationsReactivated.forEach { sharedLocationService.trackLocationUpdate(it, "Re-activated Location") }
    return mapOf(
      InternalLocationDomainEventType.LOCATION_AMENDED to amendedLocations.map { it.toDto() }.toList(),
      InternalLocationDomainEventType.LOCATION_REACTIVATED to locationsReactivated.map { it.toDto() }.toList(),
    )
  }

  private fun buildReactivationDetail(
    approvalChangeLocation: CertificationApprovalRequestLocation,
  ): ReactivationDetail? = if (approvalChangeLocation.reactivateThisLocation) {
    ReactivationDetail(
      specialistCellTypes = approvalChangeLocation.getSpecialistCellTypesFromList()?.toSet(),
      capacity = Capacity(
        maxCapacity = approvalChangeLocation.maxCapacity ?: 0,
        workingCapacity = approvalChangeLocation.workingCapacity ?: 0,
        certifiedNormalAccommodation = approvalChangeLocation.certifiedNormalAccommodation,
      ),
    )
  } else {
    null
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequestDto): ApprovalResponse {
    val approvalRequest = certificationApprovalRequestRepository.findById(rejectCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException(rejectCertificationRequest.approvalRequestReference) }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw ApprovalRequestNotInPendingStatusException(rejectCertificationRequest.approvalRequestReference)
    }

    val now = LocalDateTime.now(clock)
    val transactionInvokedBy = sharedLocationService.getUsername()
    val location = (approvalRequest as? LocationCertificationApprovalRequest)?.location

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      type = TransactionType.REJECT_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Rejection of approval request ${rejectCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (location != null) "at ${location.getKey()}" else "",
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
      newLocation = newLocation,
      prisonId = approvalRequest.prisonId,
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
    val transactionInvokedBy = sharedLocationService.getUsername()
    val newLocation = location?.isDraft() ?: false

    val linkedTransaction = sharedLocationService.createLinkedTransaction(
      type = TransactionType.WITHDRAW_CERTIFICATION_REQUEST,
      prisonId = approvalRequest.prisonId,
      detail = "Withdrawal of approval request ${withdrawCertificationRequest.approvalRequestReference} for ${approvalRequest.prisonId} " + if (location != null) "at ${location.getKey()}" else "",
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
      newLocation = newLocation,
      prisonId = approvalRequest.prisonId,
      location = location?.toDto(includeChildren = !newLocation, includeParent = !newLocation),
    ).also { linkedTransaction.txEndTime = LocalDateTime.now(clock) }
  }
}
