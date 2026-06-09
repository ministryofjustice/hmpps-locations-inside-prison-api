package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus

/**
 * Client-facing filter for listing cell certificate uploads, mapping onto the underlying statuses.
 */
enum class CellCertificateUploadStatusFilter(val statuses: List<CellCertificateUploadStatus>) {
  PROCESSING(listOf(CellCertificateUploadStatus.PENDING, CellCertificateUploadStatus.STARTED)),
  COMPLETE(listOf(CellCertificateUploadStatus.FINISHED)),
}
