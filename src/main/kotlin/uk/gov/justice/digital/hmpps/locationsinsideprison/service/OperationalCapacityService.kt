package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.OperationalCapacityRepository

@Service
@Transactional
class OperationalCapacityService(
  private val operationalCapacityRepository: OperationalCapacityRepository,
) {
  fun getOperationalCapacity(prisonId: String): OperationalCapacity? {
    return operationalCapacityRepository.findOneByPrisonId(prisonId)
  }
}
