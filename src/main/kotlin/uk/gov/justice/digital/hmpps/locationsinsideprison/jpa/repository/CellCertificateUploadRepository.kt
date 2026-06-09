package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import java.util.UUID

@Repository
interface CellCertificateUploadRepository : JpaRepository<CellCertificateUpload, UUID> {
  fun findFirstByPrisonIdAndStatusIn(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): CellCertificateUpload?

  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CellCertificateUpload>

  fun findByPrisonIdAndStatusInOrderByRequestedDateDesc(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): List<CellCertificateUpload>
}
