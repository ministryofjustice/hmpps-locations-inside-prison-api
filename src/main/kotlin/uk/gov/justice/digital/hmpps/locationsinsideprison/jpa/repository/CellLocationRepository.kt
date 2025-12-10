package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import java.util.UUID

@Repository
interface CellLocationRepository : JpaRepository<Cell, UUID> {
  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndStatus(prisonId: String, status: LocationStatus): List<Cell>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from Cell l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): Cell?
}
