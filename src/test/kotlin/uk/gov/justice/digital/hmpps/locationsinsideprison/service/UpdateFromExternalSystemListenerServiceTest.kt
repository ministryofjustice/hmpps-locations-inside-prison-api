package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.exceptions.base.MockitoException
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateFromExternalSystemEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import java.util.UUID

@ExtendWith(SpringExtension::class)
internal class UpdateFromExternalSystemListenerServiceTest {
  private val objectMapper = jacksonObjectMapper()
  private val locationService = mock<LocationService>()
  private val updateFromExternalSystemListenerService = UpdateFromExternalSystemListenerService(objectMapper, locationService)

  @BeforeEach
  internal fun setUp() {
    Mockito.reset(locationService)
  }

  @Nested
  @DisplayName("Location temporarily deactivated event")
  inner class LocationTemporarilyDeactivatedEventTests {
    private val messageId = UUID.randomUUID().toString()
    private val key = "MDI-A-1"
    private val updateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
      messageId = messageId,
      eventType = "LocationTemporarilyDeactivated",
      messageAttributes = mapOf(
        "key" to key,
        "deactivationReason" to "DAMAGED",
        "deactivationReasonDescription" to "Window broken",
        "proposedReactivationDate" to "2025-01-05",
        "planetFmReference" to "23423TH/5",
      ),
    )
    private val invalidUpdateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
      messageId = UUID.randomUUID().toString(),
      eventType = "LocationTemporarilyDeactivated",
      messageAttributes = mapOf(
        "invalidField" to "OPEN",
      ),
    )
    val location: Location? = mock()
    val id: UUID? = UUID.randomUUID()

    @Test
    fun `will process the event`() {
      whenever(locationService.getLocationByKey(key)).thenReturn(location)
      whenever(location?.id).thenReturn(id)

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      assertDoesNotThrow {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      verify(locationService, times(1)).deactivateLocations(any<DeactivateLocationsRequest>())
    }

    @Test
    fun `will throw an exception if getLocationByKey returns an error`() {
      val exceptionMessage = "Could not get location by key"
      whenever(locationService.getLocationByKey(key)).thenThrow(MockitoException(exceptionMessage))

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      val exception = assertThrows<Exception> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      assertThat(exception.message).contains(exceptionMessage)
      verify(locationService, times(0)).deactivateLocations(any<DeactivateLocationsRequest>())
    }

    @Test
    fun `will throw an exception if deactivateLocations returns an error`() {
      val exceptionMessage = "Could not deactivate locations"
      whenever(locationService.getLocationByKey(key)).thenReturn(location)
      whenever(location?.id).thenReturn(id)
      whenever(locationService.deactivateLocations(any<DeactivateLocationsRequest>())).thenThrow(MockitoException(exceptionMessage))

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      val exception = assertThrows<Exception> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      assertThat(exception.message).contains(exceptionMessage)
    }

    @Test
    fun `will throw an exception if message attributes are invalid`() {
      val message = objectMapper.writeValueAsString(invalidUpdateFromExternalSystemEvent)

      assertThrows<Exception> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      verify(locationService, times(0)).getLocationByKey(any<String>(), any<Boolean>(), any<Boolean>())
      verify(locationService, times(0)).deactivateLocations(any<DeactivateLocationsRequest>())
    }
  }

  @Test
  fun `will throw an an exception when an invalid event passed in`() {
    val messageId = UUID.randomUUID().toString()
    val message = """
    {
      "messageId" : "$messageId",
      "eventType" : "InvalidEventType",
      "description" : null,
      "messageAttributes" : {},
      "who" : "automated-test-client"
    }
    """

    val exception = assertThrows<Exception> {
      updateFromExternalSystemListenerService.onEventReceived(message)
    }
    assertThat(exception.message).contains("Cannot process event of type InvalidEventType")
  }
}
