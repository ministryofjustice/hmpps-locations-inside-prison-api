package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.util.Optional
import java.util.UUID

@Repository
interface ResidentialLocationRepository : JpaRepository<ResidentialLocation, UUID> {
  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findOneByPrisonIdAndId(prisonId: String, id: UUID): ResidentialLocation?

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndParentId(prisonId: String, parentId: UUID): List<ResidentialLocation>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from ResidentialLocation l where l.prisonId = :prisonId and l.parent is null")
  fun findAllByPrisonIdAndParentIsNull(prisonId: String): List<ResidentialLocation>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from ResidentialLocation l where l.prisonId = :prisonId and l.status = 'ARCHIVED'")
  fun findAllByPrisonIdAndArchivedIsTrue(prisonId: String): List<ResidentialLocation>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): ResidentialLocation?

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from ResidentialLocation l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): ResidentialLocation?

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: UUID): Optional<ResidentialLocation>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndParentIdAndLocalName(prisonId: String, parentId: UUID, localName: String): List<ResidentialLocation>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndParentIsNullAndLocalName(prisonId: String, localName: String): List<ResidentialLocation>
}
