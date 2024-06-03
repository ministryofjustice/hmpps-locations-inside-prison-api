package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import java.util.UUID

@Repository
interface CellLocationRepository : JpaRepository<Cell, UUID> {
  @Query("select c from Cell c join fetch c.capacity join fetch c.certification where c.prisonId = :prisonId and c.active = :active")
  fun findAllByPrisonIdAndActive(prisonId: String, active: Boolean): List<Cell>
}
