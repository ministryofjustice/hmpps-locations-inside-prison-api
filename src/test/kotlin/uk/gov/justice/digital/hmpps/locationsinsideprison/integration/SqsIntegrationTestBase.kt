package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.HMPPSDomainEvent
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : IntegrationTestBase() {

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  protected val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") as HmppsQueue }
  protected val testDomainEventQueue by lazy { hmppsQueueService.findByQueueId("test") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    auditQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(auditQueue.queueUrl).build())
    testDomainEventQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build())
    auditQueue.sqsClient.countMessagesOnQueue(auditQueue.queueUrl).get()
    testDomainEventQueue.sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get()
  }

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }

  fun getNumberOfMessagesCurrentlyOnSubscriptionQueue(): Int? =
    testDomainEventQueue.sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get()

  fun getDomainEvents(messageCount: Int = 1): List<HMPPSDomainEvent> {
    val sqsClient = testDomainEventQueue.sqsClient

    val messages: MutableList<HMPPSDomainEvent> = mutableListOf()
    await untilCallTo {
      messages.addAll(
        sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build())
          .get()
          .messages()
          .map { objectMapper.readValue(it.body(), HMPPSMessage::class.java) }
          .map { objectMapper.readValue(it.Message, HMPPSDomainEvent::class.java) },
      )
    } matches { messages.size == messageCount }

    return messages
  }

  fun assertDomainEventSent(eventType: String): HMPPSDomainEvent {
    val sqsClient = testDomainEventQueue.sqsClient
    val body = sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build()).get().messages()[0].body()
    val (message, attributes) = objectMapper.readValue(body, HMPPSMessage::class.java)
    assertThat(attributes.eventType.Value).isEqualTo(eventType)
    val domainEvent = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
    assertThat(domainEvent.eventType).isEqualTo(eventType)

    return domainEvent
  }
}

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageAttributes: HMPPSMessageAttributes,
)
