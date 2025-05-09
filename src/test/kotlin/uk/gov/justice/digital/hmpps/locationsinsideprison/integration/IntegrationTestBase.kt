package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.HmppsAuthMockServer
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.ManageUsersApiMockServer
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.PrisonApiMockServer
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.PrisonRegisterMockServer
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.PrisonerSearchMockServer
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class IntegrationTestBase : TestBase() {

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @MockitoSpyBean
  protected lateinit var telemetryClient: TelemetryClient

  @Autowired
  protected lateinit var objectMapper: ObjectMapper

  companion object {

    @JvmField
    val hmppsAuthMockServer = HmppsAuthMockServer()

    @JvmField
    val prisonerSearchMockServer = PrisonerSearchMockServer()

    @JvmField
    val prisonRegisterMockServer = PrisonRegisterMockServer()

    @JvmField
    val prisonApiMockServer = PrisonApiMockServer()

    @JvmField
    val manageUsersApiMockServer = ManageUsersApiMockServer()

    @BeforeAll
    @JvmStatic
    fun startMocks() {
      hmppsAuthMockServer.start()
      hmppsAuthMockServer.stubGrantToken()
      prisonerSearchMockServer.start()
      prisonRegisterMockServer.start()
      prisonApiMockServer.start()
      manageUsersApiMockServer.start()
    }

    @AfterAll
    @JvmStatic
    fun stopMocks() {
      manageUsersApiMockServer.stop()
      prisonApiMockServer.stop()
      prisonRegisterMockServer.stop()
      prisonerSearchMockServer.stop()
      hmppsAuthMockServer.stop()
    }
  }

  init {
    // Resolves an issue where Wiremock keeps previous sockets open from other tests causing connection resets
    System.setProperty("http.keepAlive", "false")
  }

  protected fun setAuthorisation(
    user: String? = SYSTEM_USERNAME,
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(
    clientId = "hmpps-locations-inside-prison-api",
    username = user,
    roles = roles,
    scope = scopes,
  )

  protected fun endpointRequiresAuthorisation(
    endpoint: WebTestClient.RequestHeadersSpec<*>,
    requiredRole: String? = null,
    requiredScope: String? = null,
  ): List<DynamicTest> = buildList {
    val request = endpoint.header("Content-Type", "application/json")

    add(
      DynamicTest.dynamicTest("access forbidden with no authority") {
        request
          .header(HttpHeaders.AUTHORIZATION, null)
          .exchange()
          .expectStatus().isUnauthorized
      },
    )

    add(
      DynamicTest.dynamicTest("access forbidden with no role") {
        request
          .headers(setAuthorisation())
          .exchange()
          .expectStatus().isForbidden
      },
    )

    requiredRole?.let {
      add(
        DynamicTest.dynamicTest("access forbidden with wrong role") {
          request
            .headers(setAuthorisation(roles = listOf("ROLE_INCORRECT")))
            .exchange()
            .expectStatus().isForbidden
        },
      )

      requiredScope?.let {
        add(
          DynamicTest.dynamicTest("access forbidden with right role, but wrong scope") {
            request
              .headers(setAuthorisation(roles = listOf("ROLE_$requiredRole"), scopes = listOf("incorrect")))
              .exchange()
              .expectStatus().isForbidden
          },
        )

        add(
          DynamicTest.dynamicTest("access forbidden with right scope, but wrong role") {
            request
              .headers(setAuthorisation(roles = listOf("ROLE_INCORRECT"), scopes = listOf(requiredScope)))
              .exchange()
              .expectStatus().isForbidden
          },
        )
      }
    }
  }

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String
}
