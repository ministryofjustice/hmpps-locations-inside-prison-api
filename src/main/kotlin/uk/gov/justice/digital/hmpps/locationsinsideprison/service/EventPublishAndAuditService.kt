package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.AdditionalInformation
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.SnsService
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
class EventPublishAndAuditService(
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val clock: Clock,
) {

  fun publishEvent(
    event: InternalLocationDomainEventType,
    locationDetail: LocationDTO,
    auditData: Any,
    source: InformationSource = InformationSource.DPS,
  ) {
    snsService.publishDomainEvent(
      event,
      "${locationDetail.getKey()} ${event.description}",
      occurredAt = LocalDateTime.now(clock),
      AdditionalInformation(
        id = locationDetail.id,
        key = locationDetail.getKey(),
        source = source,
      ),
    )

    auditEvent(
      event.auditType,
      locationDetail.id.toString(),
      auditData,
      source,
    )
  }

  fun auditEvent(
    auditType: AuditType,
    id: String,
    auditData: Any,
    source: InformationSource = InformationSource.DPS,
  ) {
    auditService.sendMessage(
      auditType,
      id,
      auditData,
    )
  }
}

enum class InformationSource {
  DPS, NOMIS
}
