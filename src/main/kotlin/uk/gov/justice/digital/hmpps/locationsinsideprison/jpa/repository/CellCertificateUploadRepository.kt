package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
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

  // Atomic per-row increments so the "so far" counts climb during processing and are visible to a GET
  // refresh, rather than only being written at the end in finish().
  @Modifying
  @Query("update CellCertificateUpload u set u.processedRecords = u.processedRecords + 1 where u.id = :id")
  fun incrementProcessedRecords(@Param("id") id: UUID)

  @Modifying
  @Query("update CellCertificateUpload u set u.skippedRecords = u.skippedRecords + 1 where u.id = :id")
  fun incrementSkippedRecords(@Param("id") id: UUID)

  @Modifying
  @Query("update CellCertificateUpload u set u.failedRecords = u.failedRecords + 1 where u.id = :id")
  fun incrementFailedRecords(@Param("id") id: UUID)

  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CellCertificateUpload>

  fun findByPrisonIdAndStatusInOrderByRequestedDateDesc(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): List<CellCertificateUpload>
}
