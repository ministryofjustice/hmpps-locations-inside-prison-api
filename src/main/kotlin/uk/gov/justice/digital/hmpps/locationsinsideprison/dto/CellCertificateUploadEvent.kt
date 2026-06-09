package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import java.util.UUID

/**
 * Message placed on the cell-certificate processing queue to drive the asynchronous processing of an
 * uploaded cell certificate.
 */
data class CellCertificateUploadEvent(
  val eventType: CellCertificateUploadEventType,
  val uploadId: UUID,
)

enum class CellCertificateUploadEventType {
  START_PROCESSING,
}
