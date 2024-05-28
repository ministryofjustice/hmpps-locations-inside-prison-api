package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.EventPublishAndAuditService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InformationSource
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

abstract class EventBaseResource {

  @Autowired
  private lateinit var eventPublishAndAuditService: EventPublishAndAuditService

  protected fun eventPublishAndAudit(
    event: InternalLocationDomainEventType,
    function: () -> Location,
    informationSource: InformationSource = InformationSource.DPS,
  ) =
    function().also { location ->
      eventPublishAndAuditService.publishEvent(
        eventType = event,
        locationDetail = location,
        auditData = location.copy(childLocations = null, parentLocation = null, changeHistory = null),
        source = informationSource,
      )
    }

  protected fun eventPublish(
    function: () -> Map<InternalLocationDomainEventType, List<LocationDTO>>,
  ) =
    function().onEach { (event, locationsChanged) ->
      if (locationsChanged.isNotEmpty()) {
        eventPublishAndAuditService.publishEvent(
          eventType = event,
          locationDetail = locationsChanged,
          source = InformationSource.DPS,
        )
      }
    }

  protected fun audit(auditType: AuditType, id: String, function: () -> LocationDTO) =
    function().also { auditData ->
      eventPublishAndAuditService.auditEvent(
        auditType = auditType,
        id = id,
        auditData = auditData,
      )
    }

  protected fun legacyEventPublishAndAudit(
    event: InternalLocationDomainEventType,
    function: () -> LegacyLocation,
  ) =
    function().also { location ->
      eventPublishAndAuditService.legacyPublishEvent(
        eventType = event,
        location = location,
        auditData = location.copy(changeHistory = null),
      )
    }
}
