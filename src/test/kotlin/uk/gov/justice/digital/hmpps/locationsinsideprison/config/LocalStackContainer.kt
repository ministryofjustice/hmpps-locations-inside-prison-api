package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.IOException
import java.net.ServerSocket

object LocalStackContainer {
  private val log = LoggerFactory.getLogger(this::class.java)
  val instance by lazy { startLocalstackIfNotRunning() }

  fun setLocalStackProperties(localStackContainer: LocalStackContainer, registry: DynamicPropertyRegistry) {
    val localstackUrl = localStackContainer.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
    val region = localStackContainer.region
    registry.add("hmpps.sqs.localstackUrl") { localstackUrl }
    registry.add("hmpps.sqs.region") { region }
  }

  private fun startLocalstackIfNotRunning(): LocalStackContainer? {
    TestContainersUtil.setDockerApiVersion()
    if (localstackIsRunning()) return null
    val logConsumer = Slf4jLogConsumer(log).withPrefix("localstack")
    return LocalStackContainer(
      DockerImageName.parse("localstack/localstack").withTag("4"),
    ).apply {
      withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      waitingFor(
        Wait.forLogMessage(".*Ready.*", 1),
      )
      start()
      followOutput(logConsumer)
    }
  }

  private fun localstackIsRunning(): Boolean = try {
    val serverSocket = ServerSocket(4566)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }
}

object TestContainersUtil {

  /**
   * The latest version of Docker Desktop (Mac) requires clients to use the docker API
   * with at least version 1.44, which causes issues in test containers v1.x.
   *
   * Upgrading to the latest test containers 2.x should resolve this, but in the meantime
   * we can use this workaround to force the API version
   *
   * For more information see https://github.com/testcontainers/testcontainers-java/issues/11212
   */
  fun setDockerApiVersion() {
    System.setProperty("api.version", "1.44")
  }
}
