package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationCapacity

@Repository
interface PrisonSignedOperationCapacityRepository : JpaRepository<PrisonSignedOperationCapacity, Long> {
  fun findOneByPrisonId(prisonId: String): PrisonSignedOperationCapacity?
}
