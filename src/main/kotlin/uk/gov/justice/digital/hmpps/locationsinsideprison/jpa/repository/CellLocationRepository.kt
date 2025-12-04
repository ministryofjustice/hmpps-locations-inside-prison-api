package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import java.util.Optional
import java.util.UUID

@Repository
interface CellLocationRepository : JpaRepository<Cell, UUID> {
  @Query("select c from Cell c join fetch c.capacity where c.prisonId = :prisonId and c.status = :status")
  fun findAllByPrisonIdAndStatus(prisonId: String, status: LocationStatus): List<Cell>

  @EntityGraph(value = "location.eager", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: UUID): Optional<Cell>
}
