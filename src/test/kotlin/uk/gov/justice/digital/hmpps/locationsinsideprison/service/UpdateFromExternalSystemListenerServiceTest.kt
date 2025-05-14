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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateFromExternalSystemEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationIsNotACellException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.time.LocalDateTime
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
    private val id: UUID = UUID.randomUUID()
    private val updateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
      messageId = messageId,
      eventType = "LocationTemporarilyDeactivated",
      messageAttributes = mapOf(
        "id" to id,
        "deactivationReason" to "DAMAGED",
        "deactivationReasonDescription" to "Window broken",
        "proposedReactivationDate" to "2025-01-05",
        "planetFmReference" to "23423TH/5",
      ),
    )
    val cellLocation = Location(
      id = id,
      prisonId = "MDI",
      code = "001",
      pathHierarchy = "A-1-001",
      locationType = LocationType.CELL,
      status = DerivedLocationStatus.ACTIVE,
      topLevelId = UUID.randomUUID(),
      level = 3,
      leafLevel = true,
      parentId = UUID.randomUUID(),
      lastModifiedBy = "TEST_USER",
      lastModifiedDate = LocalDateTime.now(),
    )

    @BeforeEach
    internal fun setUp() {
      whenever(locationService.getLocationById(id)).thenReturn(cellLocation)
    }

    @Test
    fun `will process the event`() {
      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      assertDoesNotThrow {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      verify(locationService, times(1)).deactivateLocations(any<DeactivateLocationsRequest>())
    }

    @Test
    fun `throw exception when no location not found`() {
      whenever(locationService.getLocationById(id)).thenReturn(null)

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      assertThrows<LocationNotFoundException> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
    }

    @Test
    fun `throw exception when no location is not a cell`() {
      whenever(locationService.getLocationById(id)).thenReturn(cellLocation.copy(locationType = LocationType.WING))

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      assertThrows<LocationIsNotACellException> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
    }

    @Test
    fun `will throw an exception if deactivateLocations returns an error`() {
      val exceptionMessage = "Could not deactivate locations"
      whenever(locationService.deactivateLocations(any<DeactivateLocationsRequest>())).thenThrow(MockitoException(exceptionMessage))

      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)

      val exception = assertThrows<Exception> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
      assertThat(exception.message).contains(exceptionMessage)
    }

    @Test
    fun `will throw an exception if message attributes are invalid`() {
      val invalidUpdateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
        messageId = UUID.randomUUID().toString(),
        eventType = "LocationTemporarilyDeactivated",
        messageAttributes = mapOf(
          "invalidField" to "invalidValue",
        ),
      )
      val message = objectMapper.writeValueAsString(invalidUpdateFromExternalSystemEvent)

      assertThrows<Exception> {
        updateFromExternalSystemListenerService.onEventReceived(message)
      }
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
