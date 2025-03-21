package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders

class ManageUsersApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8082
  }

  private val mapper = ObjectMapper().findAndRegisterModules()

  fun stubHealthPing(status: Int) {
    val stat = if (status == 200) "UP" else "DOWN"
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
          .withBody("""{"status": "$stat"}""")
          .withStatus(status),
      ),
    )
  }
}
