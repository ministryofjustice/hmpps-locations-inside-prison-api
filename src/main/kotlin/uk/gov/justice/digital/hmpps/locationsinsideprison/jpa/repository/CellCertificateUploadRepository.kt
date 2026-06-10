package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import java.util.UUID

@Repository
interface CellCertificateUploadRepository : JpaRepository<CellCertificateUpload, UUID> {
  fun findFirstByPrisonIdAndStatusIn(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): CellCertificateUpload?

  /**
   * Fetches the upload while taking a pessimistic write lock (SELECT ... FOR UPDATE) so that concurrent
   * consumers (the same SQS message redelivered to multiple pods) serialise on the row and only one can
   * claim it for processing. Must be called within a transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from CellCertificateUpload u where u.id = :id")
  fun findByIdForUpdate(@Param("id") id: UUID): CellCertificateUpload?

  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CellCertificateUpload>

  fun findByPrisonIdAndStatusInOrderByRequestedDateDesc(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): List<CellCertificateUpload>
}
