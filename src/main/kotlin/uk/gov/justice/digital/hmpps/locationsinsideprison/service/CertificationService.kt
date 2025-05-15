package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationApprovalRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock

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

  fun requestApproval(locationApprovalRequest: LocationApprovalRequest): Location {
    TODO("Not yet implemented")
  }

  fun approveCertificationRequest(approveCertificationRequest: ApproveCertificationRequest): Location {
    TODO("Not yet implemented")
  }

  fun rejectCertificationRequest(rejectCertificationRequest: RejectCertificationRequest): Location {
    TODO("Not yet implemented")
  }
}

class RejectCertificationRequest

class ApproveCertificationRequest
