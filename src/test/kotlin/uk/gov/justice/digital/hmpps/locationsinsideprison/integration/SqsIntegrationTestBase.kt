package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.HMPPSDomainEvent
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Clock

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SqsIntegrationTestBase : IntegrationTestBase() {

  @MockitoBean
  private lateinit var clock: Clock

  @BeforeEach
  fun setupClock() {
    whenever(clock.instant()).thenReturn(TestBase.clock.instant())
    whenever(clock.zone).thenReturn(TestBase.clock.zone)
  }

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  private val auditQueue by lazy { hmppsQueueService.findByQueueId("audit") as HmppsQueue }
  private val testDomainEventQueue by lazy { hmppsQueueService.findByQueueId("test") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    auditQueue.sqsClient.purgeAndAwaitEmpty(auditQueue.queueUrl)
    testDomainEventQueue.sqsClient.purgeAndAwaitEmpty(testDomainEventQueue.queueUrl)
  }

  /**
   * Empties a queue before a test runs. purgeQueue is asynchronous and does not remove messages that are in flight, so
   * we wait for the queue to be genuinely empty - counting the invisible messages too - rather than assume the purge
   * has taken effect. Without the wait a message left behind by the previous test can reappear part way through the
   * next one and fail an assertion that nothing was published.
   */
  protected fun SqsAsyncClient.purgeAndAwaitEmpty(queueUrl: String) {
    purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build()).get()
    await untilCallTo { countAllMessagesOnQueue(queueUrl).get() } matches { it == 0 }
  }

  private fun HmppsQueue.deleteMessage(receiptHandle: String) {
    sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build()).get()
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

  fun getNumberOfMessagesCurrentlyOnQueue(): Int = testDomainEventQueue.sqsClient.countMessagesOnQueue(testDomainEventQueue.queueUrl).get()

  fun purgeDomainEvents() {
    testDomainEventQueue.sqsClient.purgeAndAwaitEmpty(testDomainEventQueue.queueUrl)
  }

  fun getDomainEvents(messageCount: Int = 1): List<HMPPSDomainEvent> {
    val sqsClient = testDomainEventQueue.sqsClient

    val messages: MutableList<HMPPSDomainEvent> = mutableListOf()
    await untilCallTo {
      messages.addAll(
        sqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testDomainEventQueue.queueUrl).build())
          .get()
          .messages()
          // delete as we read, otherwise the message is merely invisible and returns to the queue once its
          // visibility timeout expires - during a later test, which then sees a message it never published
          .onEach { testDomainEventQueue.deleteMessage(it.receiptHandle()) }
          .map { objectMapper.readValue(it.body(), HMPPSMessage::class.java) }
          .map { objectMapper.readValue(it.Message, HMPPSDomainEvent::class.java) },
      )
    } matches { messages.size == messageCount }

    return messages
  }
}

data class HMPPSEventType(val Value: String, val Type: String)
data class HMPPSMessageAttributes(val eventType: HMPPSEventType)
data class HMPPSMessage(
  val Message: String,
  val MessageAttributes: HMPPSMessageAttributes,
)
