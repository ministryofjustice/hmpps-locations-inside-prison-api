package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificateLocation
import java.util.Optional
import java.util.UUID

@Repository
interface CellCertificateRepository : JpaRepository<CellCertificate, UUID> {
  @EntityGraph(value = "cell.certificate.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findByPrisonIdOrderByApprovedDateDesc(prisonId: String): List<CellCertificate>

  @EntityGraph(value = "cell.certificate.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findByPrisonIdAndCurrentIsTrue(prisonId: String): CellCertificate?

  @EntityGraph(value = "cell.certificate.graph", type = EntityGraph.EntityGraphType.LOAD)
  override fun findById(id: UUID): Optional<CellCertificate>

  @Query(
    value = """
        WITH RECURSIVE
        current_cert AS (SELECT cc.id
                         FROM cell_certificate cc
                         WHERE cc.prison_id = :prisonId
                           AND cc.current = TRUE),
        tree AS (
            SELECT ccl.*
            FROM cell_certificate_location ccl
                     JOIN current_cert c ON ccl.cell_certificate_id = c.id
            UNION ALL
            SELECT child.*
            FROM cell_certificate_location child
                     JOIN tree parent
                          ON child.parent_location_id = parent.id)
    SELECT *
    FROM tree
    WHERE path_hierarchy = :pathHierarchy
  """,
    nativeQuery = true,
  )
  fun findByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): CellCertificateLocation?
}
