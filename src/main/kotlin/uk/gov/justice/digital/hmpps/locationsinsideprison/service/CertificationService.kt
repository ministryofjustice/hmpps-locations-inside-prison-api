package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class CertificationService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
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

    if (!location.isDraft() && !location.isLocked()) {
      throw IllegalStateException("Location must be in DRAFT or LOCKED status to request approval")
    }

    val username = authenticationHolder.principal
    val now = LocalDateTime.now(clock)

    val approvalRequest = CertificationApprovalRequest(
      location = location,
      requestedBy = username,
      requestedDate = now,
      status = ApprovalRequestStatus.PENDING,
    )

    val savedRequest = certificationApprovalRequestRepository.save(approvalRequest)

    telemetryClient.trackEvent(
      "certification-approval-requested",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "requestedBy" to username,
        "approvalRequestId" to savedRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval requested for location ${location.getKey()} by $username")
    return CertificationApprovalRequestDto.from(savedRequest)
  }

  fun approveCertificationRequest(approveCertificationRequest: ApproveCertificationRequestDto): CertificationApprovalRequestDto {
    val approvalRequest = certificationApprovalRequestRepository.findById(approveCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException("Approval request not found: ${approveCertificationRequest.approvalRequestReference}") }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw IllegalStateException("Approval request is not in PENDING status")
    }

    val username = authenticationHolder.principal
    val now = LocalDateTime.now(clock)

    approvalRequest.approve(username, approveCertificationRequest.comments, now)

    // Update location status to INACTIVE (temporarily deactivated)
    val location = approvalRequest.location
    if (location.isDraft() || location.isLocked()) {
      location.status = LocationStatus.INACTIVE
      location.updatedBy = username
      location.whenUpdated = now
    }

    val savedRequest = certificationApprovalRequestRepository.save(approvalRequest)

    telemetryClient.trackEvent(
      "certification-approval-approved",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "approvedBy" to username,
        "approvalRequestId" to savedRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval approved for location ${location.getKey()} by $username")
    return CertificationApprovalRequestDto.from(savedRequest)
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequestDto): CertificationApprovalRequestDto {
    val approvalRequest = certificationApprovalRequestRepository.findById(rejectCertificationRequest.approvalRequestReference)
      .orElseThrow { ApprovalRequestNotFoundException("Approval request not found: ${rejectCertificationRequest.approvalRequestReference}") }

    if (approvalRequest.status != ApprovalRequestStatus.PENDING) {
      throw IllegalStateException("Approval request is not in PENDING status")
    }

    val username = authenticationHolder.principal
    val now = LocalDateTime.now(clock)

    approvalRequest.reject(username, rejectCertificationRequest.comments, now)

    // Update location status to DRAFT
    val location = approvalRequest.location
    if (!location.isDraft()) {
      location.status = LocationStatus.DRAFT
      location.updatedBy = username
      location.whenUpdated = now
    }

    val savedRequest = certificationApprovalRequestRepository.save(approvalRequest)

    telemetryClient.trackEvent(
      "certification-approval-rejected",
      mapOf(
        "locationId" to location.id.toString(),
        "locationKey" to location.getKey(),
        "rejectedBy" to username,
        "approvalRequestId" to savedRequest.id.toString(),
      ),
      null,
    )

    log.info("Certification approval rejected for location ${location.getKey()} by $username")
    return CertificationApprovalRequestDto.from(savedRequest)
  }
}

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(
  @Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,
)
