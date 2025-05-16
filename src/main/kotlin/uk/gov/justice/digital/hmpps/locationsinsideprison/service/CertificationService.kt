package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.util.*

@Service
@Transactional(readOnly = true)
class CertificationService(
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun requestApproval(locationApprovalRequest: LocationApprovalRequest): ApprovalRequest {
    TODO("Not yet implemented")
  }

  fun approveCertificationRequest(approveCertificationRequest: ApproveCertificationRequest): ApprovalRequest {
    TODO("Not yet implemented")
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequest): ApprovalRequest {
    TODO("Not yet implemented")
  }
}

@Schema(description = "Request to approve a location or set of locations and cells below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationApprovalRequest(
  @Schema(description = "Location Id of location requiring approval for being certified", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val locationId: UUID,
)

@Schema(description = "Approve the certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApproveCertificationRequest(
  @Schema(description = "Approval request reference", required = true)
  val approvalRequestReference: UUID,
)

@Schema(description = "Reject the certification request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RejectCertificationRequest(
  @Schema(description = "Approval request reference", required = true)
  val approvalRequestReference: UUID,
)

@Schema(description = "Approval request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApprovalRequest(
  @Schema(description = "Approval request reference", required = true)
  val approvalRequestReference: UUID,
)
