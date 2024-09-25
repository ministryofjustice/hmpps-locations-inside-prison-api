package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class SnsService(hmppsQueueService: HmppsQueueService, private val objectMapper: ObjectMapper) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val domaineventsTopic by lazy {
    hmppsQueueService.findByTopicId("domainevents")
      ?: throw RuntimeException("Topic with name domainevents doesn't exist")
  }

  @WithSpan(value = "hmpps-domain-events-topic", kind = SpanKind.PRODUCER)
  fun publishDomainEvent(
    eventType: InternalLocationDomainEventType,
    description: String,
    occurredAt: LocalDateTime,
    additionalInformation: AdditionalInformation? = null,
  ) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType.value,
        additionalInformation,
        occurredAt.atZone(ZoneId.systemDefault()).toInstant(),
        description,
      ),
    )
  }

  private fun publishToDomainEventsTopic(payload: HMPPSDomainEvent) {
    if (payload.eventType != null) {
      log.debug("Event {} for id {}", payload.eventType, payload.additionalInformation)
      domaineventsTopic.publish(
        payload.eventType,
        objectMapper.writeValueAsString(payload),
      )
        .also { log.info("Published event to outbound topic. Type: ${payload.eventType}") }
    }
  }
}

data class AdditionalInformation(
  val id: UUID? = null,
  val key: String? = null,
  val source: InformationSource? = null,
)

data class HMPPSDomainEvent(
  val eventType: String? = null,
  val additionalInformation: AdditionalInformation?,
  val version: String,
  val occurredAt: String,
  val description: String,
) {
  constructor(
    eventType: String,
    additionalInformation: AdditionalInformation?,
    occurredAt: Instant,
    description: String,
  ) : this(
    eventType,
    additionalInformation,
    "1.0",
    occurredAt.toOffsetDateFormat(),
    description,
  )
}

enum class InternalLocationDomainEventType(val value: String, val description: String, val auditType: AuditType) {
  LOCATION_CREATED(
    "location.inside.prison.created",
    "A location inside prison has been created",
    AuditType.LOCATION_CREATED,
  ),
  LOCATION_AMENDED(
    "location.inside.prison.amended",
    "A location inside prison has been amended",
    AuditType.LOCATION_AMENDED,
  ),
  LOCATION_DEACTIVATED(
    "location.inside.prison.deactivated",
    "A location inside prison has been deactivated",
    AuditType.LOCATION_DEACTIVATED,
  ),
  LOCATION_REACTIVATED(
    "location.inside.prison.reactivated",
    "A location inside prison has been reactivated",
    AuditType.LOCATION_REACTIVATED,
  ),
  LOCATION_DELETED(
    "location.inside.prison.deleted",
    "A location inside prison has been deleted",
    AuditType.LOCATION_DELETED,
  ),
  SIGNED_OP_CAP_AMENDED(
    "location.inside.prison.signed-op-cap.amended",
    "Signed Op Cap changed",
    AuditType.SIGNED_OP_CAP_AMENDED,
  ),
}

fun Instant.toOffsetDateFormat(): String =
  atZone(ZoneId.of("Europe/London")).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
