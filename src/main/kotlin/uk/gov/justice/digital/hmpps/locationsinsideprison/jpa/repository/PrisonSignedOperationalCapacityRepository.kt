package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationalCapacity

@Repository
interface PrisonSignedOperationalCapacityRepository : JpaRepository<PrisonSignedOperationalCapacity, Long> {
  fun findOneByPrisonId(prisonId: String): PrisonSignedOperationalCapacity?
}
