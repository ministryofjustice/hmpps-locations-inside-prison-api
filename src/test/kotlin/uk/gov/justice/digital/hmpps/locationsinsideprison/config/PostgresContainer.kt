package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.IOException
import java.net.ServerSocket

object PostgresContainer {
  val instance: PostgreSQLContainer<Nothing>? by lazy { startPostgresqlContainer() }

  private fun startPostgresqlContainer(): PostgreSQLContainer<Nothing>? {
    if (isPostgresRunning()) {
      log.warn("Using existing Postgres database")
      return null
    }
    log.info("Creating a Postgres database")
    return PostgreSQLContainer<Nothing>("postgres").apply {
      withEnv("HOSTNAME_EXTERNAL", "localhost")
      withDatabaseName("locations_inside_prison")
      withUsername("locations_inside_prison")
      withPassword("locations_inside_prison")
      setWaitStrategy(Wait.forListeningPort())
      withReuse(true)

      start()
    }
  }

  private fun isPostgresRunning(): Boolean = try {
    val serverSocket = ServerSocket(5432)
    serverSocket.localPort == 0
  } catch (e: IOException) {
    true
  }

  private val log = LoggerFactory.getLogger(this::class.java)
}
