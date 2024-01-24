package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NomisLocationEventListener(
  private val mapper: ObjectMapper,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    const val LOCATION_UPSERT_TYPE = "prison-offender-events.location.upsert"
  }

  @SqsListener("locationsinsideprison", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "locations-inside-prison-prisoner-event-queue", kind = SpanKind.SERVER)
  fun onEvent(requestJson: String) {
    val (message, messageAttributes) = mapper.readValue(requestJson, HMPPSMessage::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Received message $message, type $eventType")

    when (eventType) {
      LOCATION_UPSERT_TYPE -> {
        val locationEvent = mapper.readValue(message, HMPPSLocationDomainEvent::class.java)
        // DO SOMETHING HERE
        log.info("Received location event $locationEvent")
      }
      else -> {
        log.debug("Ignoring message with type $eventType")
      }
    }
  }
}

data class HMPPSLocationDomainEvent(
  val eventType: String? = null,
  val additionalInformation: AdditionalInformation,
  val version: String,
  val occurredAt: String,
  val description: String,
)

data class AdditionalInformation(
  val locationId: String,
)

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageAttributes: HMPPSMessageAttributes,
)
