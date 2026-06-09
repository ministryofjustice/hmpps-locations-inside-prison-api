package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadEventType

const val UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY = "updatecellcertificate"

/**
 * Listens for cell certificate upload processing messages and drives the asynchronous processing.
 *
 * Concurrency is capped to one message at a time per instance so each upload is processed serially; the
 * per-prison active-upload guard (DB partial unique index) prevents overlapping uploads for a prison.
 */
@Service
class CellCertificateUploadListenerService(
  private val objectMapper: ObjectMapper,
  private val cellCertificateUploadProcessingService: CellCertificateUploadProcessingService,
) {
  @SqsListener(
    UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "1",
    maxMessagesPerPoll = "1",
  )
  fun onEventReceived(rawMessage: String) {
    val event = objectMapper.readValue(rawMessage, CellCertificateUploadEvent::class.java)
    LOG.info("Received cell certificate upload event ${event.eventType} for upload ${event.uploadId}")
    when (event.eventType) {
      CellCertificateUploadEventType.START_PROCESSING -> cellCertificateUploadProcessingService.process(event.uploadId)
    }
  }

  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
