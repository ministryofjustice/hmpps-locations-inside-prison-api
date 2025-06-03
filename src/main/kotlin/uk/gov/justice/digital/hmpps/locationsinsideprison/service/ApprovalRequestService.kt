package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestNotFoundException
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ApprovalRequestService(
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
) {

  fun getApprovalRequests(prisonId: String, status: ApprovalRequestStatus? = ApprovalRequestStatus.PENDING): List<CertificationApprovalRequestDto> {
    val requests = when {
      status != null -> certificationApprovalRequestRepository.findByPrisonIdAndStatusOrderByRequestedDateDesc(prisonId, status)
      else -> certificationApprovalRequestRepository.findByPrisonIdOrderByRequestedDateDesc(prisonId)
    }

    return requests.map { CertificationApprovalRequestDto.from(it) }
  }

  fun getApprovalRequest(id: UUID): CertificationApprovalRequestDto {
    val request = certificationApprovalRequestRepository.findById(id)
      .orElseThrow { ApprovalRequestNotFoundException(id) }

    return CertificationApprovalRequestDto.from(request)
  }
}
