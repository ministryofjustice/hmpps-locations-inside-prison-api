package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import java.util.UUID

@Repository
interface NonResidentialLocationRepository : JpaRepository<NonResidentialLocation, UUID> {
  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): Location?
}
