package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface CellCertificateUploadRepository : JpaRepository<CellCertificateUpload, UUID> {
  fun findFirstByPrisonIdAndModeAndStatusIn(prisonId: String, mode: CellCertificateUploadMode, statuses: Collection<CellCertificateUploadStatus>): CellCertificateUpload?

  /** The import a preview was continued as, if it has been continued. */
  fun findFirstByPreviewUploadId(previewUploadId: UUID): CellCertificateUpload?

  /** Whether an import for the prison finished after the given time, which makes an earlier preview out of date. */
  fun existsByPrisonIdAndModeAndStatusAndEndTimeAfter(
    prisonId: String,
    mode: CellCertificateUploadMode,
    status: CellCertificateUploadStatus,
    endTime: LocalDateTime,
  ): Boolean

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

  @Modifying
  @Query("update CellCertificateUpload u set u.discrepancyRecords = u.discrepancyRecords + 1 where u.id = :id")
  fun incrementDiscrepancyRecords(@Param("id") id: UUID)

  fun findByCertificationApprovalRequestId(certificationApprovalRequestId: UUID): CellCertificateUpload?

  fun findByPrisonIdOrderByRequestedDateDesc(prisonId: String): List<CellCertificateUpload>

  fun findByPrisonIdAndStatusInOrderByRequestedDateDesc(prisonId: String, statuses: Collection<CellCertificateUploadStatus>): List<CellCertificateUpload>
}
