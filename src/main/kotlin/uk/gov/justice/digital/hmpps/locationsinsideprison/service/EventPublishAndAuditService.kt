package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
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
    publishTree(eventType = eventType, locations = locationDetail, source = source)
    locationDetail.forEach { auditEvent(eventType.auditType, it.id.toString(), it) }
  }

  fun publishEvent(
    eventType: InternalLocationDomainEventType,
    locationDetail: NonResidentialLocationDTO,
    auditData: Any? = null,
    auditType: AuditType = eventType.auditType,
  ) {
    publishEvent(event = eventType, location = locationDetail)
    auditData?.let { auditEvent(auditType, locationDetail.id.toString(), it) }
  }

  fun publishEvent(
    eventType: InternalLocationDomainEventType,
    locationDetail: LocationDTO,
    auditData: Any? = null,
    source: InformationSource = InformationSource.DPS,
    auditType: AuditType = eventType.auditType,
  ) {
    publishTree(eventType = eventType, locations = listOf(locationDetail), source = source)
    auditData?.let { auditEvent(auditType, locationDetail.id.toString(), it) }
  }

  /**
   * Publishes an amended event for every location changed by an operation (the targets and any knock-on
   * changes such as parents) and audits the targets with the audit type describing what happened to them.
   * Returns the targets so callers can use them as the response body.
   */
  fun publishAndAudit(
    result: LocationChangeResult,
    source: InformationSource = InformationSource.DPS,
  ): List<LocationDTO> {
    publishTree(
      eventType = InternalLocationDomainEventType.LOCATION_AMENDED,
      locations = result.changed + result.alsoAmended,
      source = source,
    )
    result.changed.forEach {
      auditEvent(result.auditType, it.id.toString(), it.copy(childLocations = null, parentLocation = null, changeHistory = null))
    }
    return result.changed
  }

  /**
   * Publishes [eventType] for each location and all of its sub-locations, then an amended event for each
   * ancestor, de-duplicated so that a location receives at most one event of a given type per call.
   */
  private fun publishTree(
    eventType: InternalLocationDomainEventType,
    locations: List<LocationDTO>,
    source: InformationSource,
  ) {
    val toPublish = LinkedHashMap<Pair<InternalLocationDomainEventType, UUID?>, Location>()
    locations.forEach { root ->
      root.getSubLocations().forEach { toPublish.putIfAbsent(eventType to it.id, it) }
      generateSequence(root.parentLocation) { it.parentLocation }.forEach {
        toPublish.putIfAbsent(InternalLocationDomainEventType.LOCATION_AMENDED to it.id, it)
      }
    }
    toPublish.forEach { (key, location) -> publishEvent(event = key.first, location = location, source = source) }
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
  }

  private fun publishEvent(
    event: InternalLocationDomainEventType,
    location: NonResidentialLocationDTO,
  ) {
    if (location.status != DerivedLocationStatus.DRAFT) {
      snsService.publishDomainEvent(
        eventType = event,
        description = "[${location.getKey()}] : ${location.localName} ${event.description}",
        occurredAt = LocalDateTime.now(clock),
        additionalInformation = AdditionalInformation(
          id = location.id,
          key = location.getKey(),
          source = InformationSource.DPS,
        ),
      )
    }
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

/**
 * The outcome of an operation that changes locations. Every location in [changed] and [alsoAmended] is published
 * as amended; only [changed] (the targets of the operation) are audited, with [auditType] recording what happened.
 */
data class LocationChangeResult(
  val auditType: AuditType,
  val changed: List<LocationDTO>,
  val alsoAmended: List<LocationDTO> = emptyList(),
)
