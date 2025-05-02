package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateFromExternalSystemEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest

const val UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY = "updatefromexternalsystemeventsqueue"

@Service
class UpdateFromExternalSystemListenerService(
  private val objectMapper: ObjectMapper,
  private val locationService: LocationService,
) {
  private companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY, factory = "hmppsQueueContainerFactoryProxy")
  fun onEventReceived(
    rawMessage: String,
  ) {
    try {
      LOG.info("Received update from external system event: $rawMessage")
      val sqsMessage = objectMapper.readValue(rawMessage, UpdateFromExternalSystemEvent::class.java)
      when (sqsMessage.eventType) {
        "LocationTemporarilyDeactivated" -> {
          val temporarilyDeactivateLocationByKeyRequest = sqsMessage.toTemporarilyDeactivateLocationByKeyRequest()
          val key = temporarilyDeactivateLocationByKeyRequest.key
          val location = locationService.getLocationByKey(key) ?: throw Exception("No location found for key: $key")
          val temporaryDeactivationLocationRequest = TemporaryDeactivationLocationRequest(
            deactivationReason = temporarilyDeactivateLocationByKeyRequest.deactivationReason,
            deactivationReasonDescription = temporarilyDeactivateLocationByKeyRequest.deactivationReasonDescription,
            proposedReactivationDate = temporarilyDeactivateLocationByKeyRequest.proposedReactivationDate,
            planetFmReference = temporarilyDeactivateLocationByKeyRequest.planetFmReference,
          )
          locationService.deactivateLocations(DeactivateLocationsRequest(mapOf(location.id to temporaryDeactivationLocationRequest)))
          LOG.info("Location temporarily deactivated: $key (${location.id})")
        }
        else -> throw Exception("Cannot process event of type ${sqsMessage.eventType}")
      }
    } catch (e: Exception) {
      LOG.error("Error processing update from external system event", e)
      throw e
    }
  }
}
