package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.EventPublishAndAuditService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InformationSource
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationChangeResult
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialLocationDTO
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

abstract class EventBase {

  @Autowired
  private lateinit var eventPublishAndAuditService: EventPublishAndAuditService

  protected fun publishSignedOpCapChange(
    function: () -> SignedOperationCapacityDto,
  ) = function().also { signedOpCap ->
    eventPublishAndAuditService.signedOpCapEvent(
      eventType = InternalLocationDomainEventType.SIGNED_OP_CAP_AMENDED,
      signedOperationCapacity = signedOpCap,
      auditData = signedOpCap,
    )
  }

  protected fun eventPublishAndAudit(
    event: InternalLocationDomainEventType,
    auditType: AuditType = event.auditType,
    function: () -> Location,
  ) = function().also { location ->
    eventPublishAndAuditService.publishEvent(
      eventType = event,
      locationDetail = location,
      auditData = location.copy(childLocations = null, parentLocation = null, changeHistory = null),
      source = InformationSource.DPS,
      auditType = auditType,
    )
  }

  protected fun eventPublishNonResiAndAudit(
    event: InternalLocationDomainEventType,
    auditType: AuditType = event.auditType,
    function: () -> NonResidentialLocationDTO,
  ) = function().also { location ->
    eventPublishAndAuditService.publishEvent(
      eventType = event,
      locationDetail = location,
      auditData = location,
      auditType = auditType,
    )
  }

  /**
   * Publishes amended events for everything the operation changed, audits the targets with the result's audit
   * type and returns the targets for use as the response body.
   */
  protected fun publishAndAudit(result: LocationChangeResult): List<Location> = eventPublishAndAuditService.publishAndAudit(result)

  protected fun eventPublish(
    function: () -> Map<InternalLocationDomainEventType, List<LocationDTO>>,
  ) = function().onEach { (event, locationsChanged) ->
    if (locationsChanged.isNotEmpty()) {
      eventPublishAndAuditService.publishEvent(
        eventType = event,
        locationDetail = locationsChanged,
        source = InformationSource.DPS,
      )
    }
  }

  protected fun audit(id: String, function: () -> Location) = function().also { auditData ->
    eventPublishAndAuditService.auditEvent(
      auditType = AuditType.LOCATION_AMENDED,
      id = id,
      auditData = auditData,
    )
  }

  protected fun legacyEventPublishAndAudit(
    event: InternalLocationDomainEventType,
    function: () -> LegacyLocation,
  ) = function().also { location ->
    eventPublishAndAuditService.legacyPublishEvent(
      eventType = event,
      location = location,
      auditData = location.copy(changeHistory = null),
    )
  }
}
