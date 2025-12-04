package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificate
import java.util.Optional
import java.util.UUID

@Repository
interface CellCertificateRepository : JpaRepository<CellCertificate, UUID> {
  @EntityGraph(value = "cellCertificate.eager", type = EntityGraph.EntityGraphType.LOAD)
  fun findByPrisonIdOrderByApprovedDateDesc(prisonId: String): List<CellCertificate>

  @EntityGraph(value = "cellCertificate.eager", type = EntityGraph.EntityGraphType.LOAD)
  fun findByPrisonIdAndCurrentIsTrue(prisonId: String): CellCertificate?

  @EntityGraph(value = "cellCertificate.eager", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: UUID): Optional<CellCertificate>
}
