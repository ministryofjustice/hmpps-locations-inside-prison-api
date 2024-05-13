package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.util.*

@Repository
interface ResidentialLocationRepository : JpaRepository<ResidentialLocation, UUID> {
  fun findAllByPrisonIdAndParentId(prisonId: String, parentId: UUID): List<ResidentialLocation>
  fun findAllByPrisonIdAndParentIsNull(prisonId: String): List<ResidentialLocation>
  fun findAllByPrisonIdAndArchivedIsTrue(prisonId: String): List<ResidentialLocation>

}
