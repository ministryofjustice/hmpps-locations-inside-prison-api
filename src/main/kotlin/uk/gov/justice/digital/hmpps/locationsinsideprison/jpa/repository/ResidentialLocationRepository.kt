package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.util.UUID

@Repository
interface ResidentialLocationRepository : JpaRepository<ResidentialLocation, UUID> {
  fun findOneByPrisonIdAndId(prisonId: String, id: UUID): ResidentialLocation?
  fun findAllByPrisonIdAndParentId(prisonId: String, parentId: UUID): List<ResidentialLocation>

  @Query("select l from ResidentialLocation l left join fetch l.capacity c where l.prisonId = :prisonId and l.parent is null")
  fun findAllByPrisonIdAndParentIsNull(prisonId: String): List<ResidentialLocation>
  fun findAllByPrisonIdAndArchivedIsTrue(prisonId: String): List<ResidentialLocation>
  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): ResidentialLocation?

  @Query("select l from ResidentialLocation l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): ResidentialLocation?
}
