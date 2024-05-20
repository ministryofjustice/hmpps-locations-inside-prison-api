package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.util.UUID

@Repository
interface LocationRepository : JpaRepository<Location, UUID> {
  fun findAllByPrisonIdOrderByPathHierarchy(prisonId: String): List<Location>

  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): Location?

  fun findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId: String, locationType: LocationType): List<Location>

  fun findAllByPrisonIdAndPathHierarchyIsIn(prisonId: String, pathHierarchy: List<String>): List<Location>
}
