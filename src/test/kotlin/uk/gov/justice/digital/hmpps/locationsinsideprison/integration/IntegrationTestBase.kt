package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.JwtAuthHelper
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.HmppsAuthMockServer

@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class IntegrationTestBase : TestBase() {

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @SpyBean
  protected lateinit var telemetryClient: TelemetryClient

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  companion object {

    @JvmField
    val hmppsAuthMockServer = HmppsAuthMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      hmppsAuthMockServer.start()
      hmppsAuthMockServer.stubGrantToken()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      hmppsAuthMockServer.stop()
    }
  }

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  protected fun setAuthorisation(
    user: String = SYSTEM_USERNAME,
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String
}
