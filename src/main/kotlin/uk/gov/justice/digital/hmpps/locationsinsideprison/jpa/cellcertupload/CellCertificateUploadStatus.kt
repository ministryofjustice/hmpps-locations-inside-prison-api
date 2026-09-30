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

/**
 * Whether an upload is a preview (every change is worked out and then undone, so nothing is changed) or a
 * real import. A preview is the only route to an import: continuing one copies its rows into a new import.
 */
enum class CellCertificateUploadMode(val description: String) {
  PREVIEW("Works out what the import would do without changing anything"),
  IMPORT("Changes the locations and creates a new cell certificate"),
}
