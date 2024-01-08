package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationDetail
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AuditType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.EventPublishAndAuditService
import uk.gov.justice.digital.hmpps.locationsinsideprison.services.InternalLocationDomainEventType

abstract class EventBaseResource {

  @Autowired
  private lateinit var eventPublishAndAuditService: EventPublishAndAuditService

  protected fun eventPublishAndAuditWrapper(event: InternalLocationDomainEventType, function: () -> LocationDetail) =
    function().also { location -> eventPublishAndAuditService.publishEvent(event, location, location) }

  protected fun auditWrapper(auditType: AuditType, id: String, function: () -> LocationDetail) =
    function().also { auditData -> eventPublishAndAuditService.auditEvent(auditType, id, auditData) }
}
