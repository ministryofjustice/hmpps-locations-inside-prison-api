package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificate
import java.util.UUID

@Repository
interface CellCertificateRepository : JpaRepository<CellCertificate, UUID> {
  fun findByPrisonIdOrderByApprovedDateDesc(prisonId: String): List<CellCertificate>
  fun findByPrisonIdAndCurrentIsTrue(prisonId: String): CellCertificate?
}
