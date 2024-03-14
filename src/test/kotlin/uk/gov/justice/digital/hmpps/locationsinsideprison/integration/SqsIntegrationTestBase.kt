package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.LocalStackContainer.setLocalStackProperties
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
}
