package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import java.util.UUID

@Repository
interface LocationRepository : JpaRepository<Location, UUID> {
  fun findOneByPrisonIdAndCode(prisonId: String, code: String): Location?
}
