package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonDto

class PrisonRegisterMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8095
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

  fun stubLookupPrison(
    prisonId: String,
    female: Boolean = false,
    lthse: Boolean = false,
    returnResult: Boolean = true,
  ) {
    if (returnResult) {
      stubFor(
        get("/prisons/id/$prisonId")
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(
                mapper.writeValueAsBytes(
                  PrisonDto(
                    prisonId = prisonId,
                    prisonName = prisonId,
                    male = !female,
                    female = female,
                    lthse = lthse,
                    active = true,
                    contracted = false,
                    categories = emptySet(),
                  ),
                ),
              )
              .withStatus(200),
          ),
      )
    } else {
      stubFor(
        get("/prisons/id/$prisonId")
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(404),
          ),
      )
    }
  }
}
