package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
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
    eventType: InternalLocationDomainEventType,
    locationDetail: List<LocationDTO>,
    auditData: Any? = null,
    source: InformationSource = InformationSource.DPS,
  ) {
    locationDetail.forEach {
      publishEvent(eventType = eventType, locationDetail = it, auditData = it, source = source)
    }
  }

  fun publishEvent(
    eventType: InternalLocationDomainEventType,
    locationDetail: LocationDTO,
    auditData: Any? = null,
    source: InformationSource = InformationSource.DPS,
  ) {
    locationDetail.getSubLocations().forEach {
      publishEvent(event = eventType, location = it, source = source)
    }
    traverseUp(eventType = InternalLocationDomainEventType.LOCATION_AMENDED, location = locationDetail.parentLocation, source = source)

    auditData?.let {
      auditEvent(
        auditType = eventType.auditType,
        id = locationDetail.id.toString(),
        auditData = it,
        source = source,
      )
    }
  }

  private fun traverseUp(eventType: InternalLocationDomainEventType, location: Location?, source: InformationSource) {
    if (location != null) {
      publishEvent(event = eventType, location = location, source = source)
      traverseUp(eventType = eventType, location = location.parentLocation, source = source)
    }
  }
  private fun publishEvent(
    event: InternalLocationDomainEventType,
    location: Location,
    source: InformationSource,
  ) {
    snsService.publishDomainEvent(
      eventType = event,
      description = "${location.getKey()} ${event.description}",
      occurredAt = LocalDateTime.now(clock),
      additionalInformation = AdditionalInformation(
        id = location.id,
        key = location.getKey(),
        source = source,
      ),
    )
  }

  fun auditEvent(
    auditType: AuditType,
    id: String,
    auditData: Any,
    source: InformationSource = InformationSource.DPS,
  ) {
    auditService.sendMessage(
      auditType = auditType,
      id = id,
      details = auditData,
    )
  }
}

enum class InformationSource {
  DPS,
  NOMIS,
}
