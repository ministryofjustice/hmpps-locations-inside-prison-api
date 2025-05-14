package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateFromExternalSystemEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.EventBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationIsNotACellException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException

const val UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY = "updatefromexternalsystemevents"

@Service
class UpdateFromExternalSystemListenerService(
  private val objectMapper: ObjectMapper,
  private val locationService: LocationService,
) : EventBase() {
  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun onEventReceived(
    rawMessage: String,
  ) {
      LOG.info("Received update from external system event: $rawMessage")
      val sqsMessage = objectMapper.readValue(rawMessage, UpdateFromExternalSystemEvent::class.java)
      when (sqsMessage.eventType) {
        "LocationTemporarilyDeactivated" -> {
          val event = sqsMessage.toUpdateFromExternalSystemDeactivateEvent()
          val temporaryDeactivationLocationRequest = event.let {
            TemporaryDeactivationLocationRequest(
              deactivationReason = it.deactivationReason,
              deactivationReasonDescription = it.deactivationReasonDescription,
              proposedReactivationDate = it.proposedReactivationDate,
              planetFmReference = it.planetFmReference,
            )
          }
          val location = locationService.getLocationById(event.id)
          if (location == null) {
            throw LocationNotFoundException(event.id.toString())
          }
          if (location.locationType != LocationType.CELL) {
            throw LocationIsNotACellException(location.getKey())
          }
          val deactivateLocationsRequest = DeactivateLocationsRequest(mapOf(event.id to temporaryDeactivationLocationRequest))
          deactivate(locationService.deactivateLocations(deactivateLocationsRequest))
        }
        else -> throw Exception("Cannot process event of type ${sqsMessage.eventType}")
      }
  }
}
