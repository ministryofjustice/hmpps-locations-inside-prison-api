package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
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
    source: InformationSource = InformationSource.DPS,
  ) {
    locationDetail.forEach {
      publishEvent(eventType = eventType, locationDetail = it, auditData = it, source = source)
    }
  }

  fun publishEvent(
    eventType: InternalLocationDomainEventType,
    locationDetail: NonResidentialLocationDTO,
    auditData: Any? = null,
  ) {
    publishEvent(event = eventType, location = locationDetail)
    auditData?.let { auditEvent(eventType.auditType, locationDetail.id.toString(), it) }
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
    auditData?.let { auditEvent(eventType.auditType, locationDetail.id.toString(), it) }
  }

  private fun traverseUp(eventType: InternalLocationDomainEventType, location: Location?, source: InformationSource) {
    if (location != null) {
      publishEvent(event = eventType, location = location, source = source)
      traverseUp(eventType = eventType, location = location.parentLocation, source = source)
    }
  }

  fun legacyPublishEvent(
    eventType: InternalLocationDomainEventType,
    location: LegacyLocation,
    auditData: Any? = null,
  ) {
    snsService.publishDomainEvent(
      eventType = eventType,
      description = "${location.getKey()} ${eventType.description}",
      occurredAt = LocalDateTime.now(clock),
      additionalInformation = AdditionalInformation(
        id = location.id,
        key = location.getKey(),
        source = InformationSource.NOMIS,
      ),
    )

    auditData?.let {
      auditEvent(
        auditType = eventType.auditType,
        id = location.id.toString(),
        auditData = it,
      )
    }
  }

  private fun publishEvent(
    event: InternalLocationDomainEventType,
    location: Location,
    source: InformationSource,
  ) {
    if (location.status != DerivedLocationStatus.DRAFT) {
      val occurredAt = LocalDateTime.now(clock)
      event.withCompanionAmendedEvent().forEach { eventToPublish ->
        snsService.publishDomainEvent(
          eventType = eventToPublish,
          description = "${location.getKey()} ${eventToPublish.description}",
          occurredAt = occurredAt,
          additionalInformation = AdditionalInformation(
            id = location.id,
            key = location.getKey(),
            source = source,
          ),
        )
      }
    }
  }

  private fun publishEvent(
    event: InternalLocationDomainEventType,
    location: NonResidentialLocationDTO,
  ) {
    if (location.status != DerivedLocationStatus.DRAFT) {
      val occurredAt = LocalDateTime.now(clock)
      event.withCompanionAmendedEvent().forEach { eventToPublish ->
        snsService.publishDomainEvent(
          eventType = eventToPublish,
          description = "[${location.getKey()}] : ${location.localName} ${eventToPublish.description}",
          occurredAt = occurredAt,
          additionalInformation = AdditionalInformation(
            id = location.id,
            key = location.getKey(),
            source = InformationSource.DPS,
          ),
        )
      }
    }
  }

  /**
   * Transitional (MAPA-346): the NOMIS sync consumer is moving to listen only to created/amended/deleted,
   * so every deactivated/reactivated event is accompanied by an amended event for the same location.
   * Remove once deactivated/reactivated are retired (MAPA-347).
   */
  private fun InternalLocationDomainEventType.withCompanionAmendedEvent(): List<InternalLocationDomainEventType> = when (this) {
    InternalLocationDomainEventType.LOCATION_DEACTIVATED,
    InternalLocationDomainEventType.LOCATION_REACTIVATED,
    -> listOf(this, InternalLocationDomainEventType.LOCATION_AMENDED)
    else -> listOf(this)
  }

  fun auditEvent(
    auditType: AuditType,
    id: String,
    auditData: Any,
  ) {
    auditService.sendMessage(
      auditType = auditType,
      id = id,
      details = auditData,
    )
  }

  fun signedOpCapEvent(
    eventType: InternalLocationDomainEventType,
    signedOperationCapacity: SignedOperationCapacityDto,
    auditData: Any? = null,
  ) {
    snsService.publishDomainEvent(
      eventType = eventType,
      description = "Signed Op-Cap changed for ${signedOperationCapacity.prisonId} to ${signedOperationCapacity.signedOperationCapacity}",
      occurredAt = LocalDateTime.now(clock),
      additionalInformation = AdditionalInformation(
        key = signedOperationCapacity.prisonId,
        source = InformationSource.DPS,
      ),
    )

    auditData?.let {
      auditEvent(
        auditType = eventType.auditType,
        id = signedOperationCapacity.prisonId,
        auditData = it,
      )
    }
  }
}

enum class InformationSource {
  DPS,
  NOMIS,
}
