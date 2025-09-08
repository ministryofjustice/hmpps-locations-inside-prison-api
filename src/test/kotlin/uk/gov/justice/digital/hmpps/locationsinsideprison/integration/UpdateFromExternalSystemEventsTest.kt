package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateFromExternalSystemEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.UUID

class UpdateFromExternalSystemEventsTest : CommonDataTestBase() {
  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @MockitoBean
  lateinit var hmppsAuthenticationHolder: HmppsAuthenticationHolder

  @MockitoSpyBean
  lateinit var locationService: LocationService

  private val updateFromExternalSystemEventsQueue by lazy {
    hmppsQueueService.findByQueueId(UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY) ?: throw MissingQueueException("HmppsQueue $UPDATE_FROM_EXTERNAL_SYSTEM_QUEUE_CONFIG_KEY not found")
  }
  internal val queueSqsClient by lazy { updateFromExternalSystemEventsQueue.sqsClient }
  internal val queueUrl by lazy { updateFromExternalSystemEventsQueue.queueUrl }
  internal val queueSqsDlqClient by lazy { updateFromExternalSystemEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val dlqUrl by lazy { updateFromExternalSystemEventsQueue.dlqUrl as String }

  override fun getNumberOfMessagesCurrentlyOnQueue(): Int = queueSqsClient.countAllMessagesOnQueue(queueUrl).get()
  fun getNumberOfMessagesCurrentlyOnDlq(): Int = queueSqsDlqClient.countAllMessagesOnQueue(dlqUrl).get()

  @BeforeEach
  fun setup() {
    Mockito.reset(locationService)
    Mockito.reset(hmppsAuthenticationHolder)

    whenever(hmppsAuthenticationHolder.username).thenReturn("User 1")

    queueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build())
    queueSqsDlqClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(dlqUrl).build())
  }

  @Nested
  @DisplayName("Location temporarily deactivated event")
  inner class LocationTemporarilyDeactivatedEventTests {
    @Test
    fun `will process an event`() {
      prisonerSearchMockServer.stubSearchByLocations(
        cell1.prisonId,
        listOf(cell1.getPathHierarchy()),
        false, // Return no results as this will cause deactivate to fail
      )

      val messageId = UUID.randomUUID().toString()
      val updateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
        messageId = messageId,
        eventType = "LocationTemporarilyDeactivated",
        messageAttributes = mapOf(
          "id" to cell1.id,
          "deactivationReason" to "DAMAGED",
          "deactivationReasonDescription" to "Window broken",
          "proposedReactivationDate" to "2025-01-05",
          "planetFmReference" to "23423TH/5",
          "updatedBy" to "EXTERNAL_USER_1",
        ),
      )
      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)
      queueSqsClient.sendMessage(
        SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build(),
      )

      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 1 }
      await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
      await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }

      await untilAsserted { verify(locationService, times(1)).deactivateLocations(any<DeactivateLocationsRequest>()) }

      val parentLocations = cell1.getParentLocations()
      getDomainEvents(parentLocations.size + 1).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrderElementsOf(
          buildList {
            add(InternalLocationDomainEventType.LOCATION_DEACTIVATED.value to cell1.getKey())
            addAll(parentLocations.map { parentLocation -> InternalLocationDomainEventType.LOCATION_AMENDED.value to parentLocation.getKey() })
          },
        )
      }
    }

    @Test
    fun `will throw an exception if location is not a cell`() {
      prisonerSearchMockServer.stubSearchByLocations(
        landingN1.prisonId,
        landingN1.cellLocations().map { it.getPathHierarchy() },
        false, // Return no results as this will cause deactivate to fail
      )

      val messageId = UUID.randomUUID().toString()
      val updateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
        messageId = messageId,
        eventType = "LocationTemporarilyDeactivated",
        messageAttributes = mapOf(
          "id" to landingN1.id,
          "deactivationReason" to "DAMAGED",
          "deactivationReasonDescription" to "Window broken",
          "proposedReactivationDate" to "2025-01-05",
          "planetFmReference" to "23423TH/5",
          "updatedBy" to "EXTERNAL_USER_2",
        ),
      )
      val message = objectMapper.writeValueAsString(updateFromExternalSystemEvent)
      queueSqsClient.sendMessage(
        SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build(),
      )

      await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 1 }
      await untilAsserted {
        verify(
          locationService,
          times(0),
        ).deactivateLocations(any<DeactivateLocationsRequest>())
      }
    }

    @Test
    fun `will throw an exception if message attributes are invalid`() {
      val invalidUpdateFromExternalSystemEvent = UpdateFromExternalSystemEvent(
        messageId = UUID.randomUUID().toString(),
        eventType = "LocationTemporarilyDeactivated",
        messageAttributes = mapOf(
          "invalidField" to "OPEN",
        ),
      )
      val message = objectMapper.writeValueAsString(invalidUpdateFromExternalSystemEvent)
      queueSqsClient.sendMessage(
        SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build(),
      )

      await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 1 }
      await untilAsserted {
        verify(
          locationService,
          times(0),
        ).deactivateLocations(any<DeactivateLocationsRequest>())
      }
    }
  }

  @Test
  fun `will handle a test event`() {
    val messageId = UUID.randomUUID().toString()
    val message = """
    {
      "messageId" : "$messageId",
      "eventType" : "TestEvent",
      "description" : null,
      "messageAttributes" : {}
    }
    """

    queueSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build(),
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
  }

  @Test
  fun `will write an invalid event to the dlq`() {
    val messageId = UUID.randomUUID().toString()
    val message = """
    {
      "messageId" : "$messageId",
      "eventType" : "InvalidEventType",
      "description" : null,
      "messageAttributes" : {}
    }
    """

    queueSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(message).build(),
    )

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 1 }
  }
}
