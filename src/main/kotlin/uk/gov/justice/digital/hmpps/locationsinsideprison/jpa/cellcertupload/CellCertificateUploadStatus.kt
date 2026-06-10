package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload

/**
 * Status of a cell certificate upload (master record).
 */
enum class CellCertificateUploadStatus(val description: String) {
  PENDING("Stored and waiting to be processed"),
  STARTED("Processing has started"),
  FINISHED("Processing has finished"),
}

/**
 * Outcome of processing a single uploaded cell (detail record).
 */
enum class CellCertificateUploadLocationStatus(val description: String) {
  PENDING("Not yet processed"),
  PROCESSED("Successfully processed"),
  SKIPPED("Skipped, no change required or not applicable"),
  FAILED("Processing failed"),
}
