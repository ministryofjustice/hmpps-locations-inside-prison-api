package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationDetail
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.AdditionalInformation
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.SnsService
import java.time.Clock
import java.time.LocalDateTime

@Service
class EventPublishAndAuditService(
  private val snsService: SnsService,
  private val auditService: AuditService,
  private val clock: Clock,
) {

  fun publishEvent(
    event: InternalLocationDomainEventType,
    locationDetail: LocationDetail,
    auditData: Any,
    source: InformationSource = InformationSource.DPS,
  ) {
    snsService.publishDomainEvent(
      event,
      "${event.description} ${locationDetail.name}",
      occurredAt = LocalDateTime.now(clock),
      AdditionalInformation(
        id = locationDetail.id,
        name = locationDetail.name,
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
