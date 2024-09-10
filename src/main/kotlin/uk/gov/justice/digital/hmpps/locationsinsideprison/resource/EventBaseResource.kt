package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.EventPublishAndAuditService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InformationSource
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

abstract class EventBaseResource {

  @Autowired
  private lateinit var eventPublishAndAuditService: EventPublishAndAuditService

  protected fun publishSignedOpCapChange(
    event: InternalLocationDomainEventType,
    function: () -> SignedOperationCapacityDto,
  ) =
    function().also { signedOpCap ->
      eventPublishAndAuditService.signedOpCapEvent(
        eventType = event,
        signedOperationCapacity = signedOpCap,
        auditData = signedOpCap,
      )
    }

  protected fun eventPublishAndAudit(
    event: InternalLocationDomainEventType,
    function: () -> Location,
  ) =
    function().also { location ->
      eventPublishAndAuditService.publishEvent(
        eventType = event,
        locationDetail = location,
        auditData = location.copy(childLocations = null, parentLocation = null, changeHistory = null),
        source = InformationSource.DPS,
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

  protected fun audit(id: String, function: () -> Location) =
    function().also { auditData ->
      eventPublishAndAuditService.auditEvent(
        auditType = AuditType.LOCATION_AMENDED,
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

  protected fun reactivate(reactivatedLocations: Map<InternalLocationDomainEventType, List<LocationDTO>>): List<Location> {
    return auditAndEmit(InternalLocationDomainEventType.LOCATION_REACTIVATED, reactivatedLocations)
  }

  protected fun deactivate(deactivatedLocations: Map<InternalLocationDomainEventType, List<LocationDTO>>): List<Location> {
    return auditAndEmit(InternalLocationDomainEventType.LOCATION_DEACTIVATED, deactivatedLocations)
  }

  protected fun update(updatedLocations: Map<InternalLocationDomainEventType, List<LocationDTO>>): List<Location> {
    return auditAndEmit(InternalLocationDomainEventType.LOCATION_AMENDED, updatedLocations)
  }

  private fun auditAndEmit(eventType: InternalLocationDomainEventType, locations: Map<InternalLocationDomainEventType, List<Location>>): List<Location> {
    eventPublish { locations }
    locations[eventType]?.forEach {
      audit(it.getKey()) { it.copy(childLocations = null, parentLocation = null) }
    }
    return locations[eventType] ?: emptyList()
  }
}
