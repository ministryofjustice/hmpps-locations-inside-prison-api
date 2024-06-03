package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationHistory
import java.util.UUID

@Repository
interface LocationHistoryRepository : JpaRepository<LocationHistory, Long> {
  fun findTop10ByLocationIdOrderByAmendedDateDesc(locationId: UUID): List<LocationHistory>
}
