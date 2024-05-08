package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AttributeQuery
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.AttributeSearch
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Matcher
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SearchResult

class PrisonerSearchMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
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

  fun stubSearchByLocations(
    prisonId: String,
    locations: List<String>,
    returnResult: Boolean = false,
  ) {
    val requestBody = mapper.writeValueAsString(
      /* value = */
      AttributeSearch(
        queries = listOf(
          AttributeQuery(
            matchers = listOf(
              Matcher(attribute = "prisonId", condition = "IS", searchTerm = prisonId),
              Matcher(attribute = "cellLocation", condition = "IN", searchTerm = locations.joinToString(",")),
            ),
          ),
        ),
      ),
    )

    var result = SearchResult(
      content = mutableListOf(),
    )
    if (returnResult) {
      result = result.copy(
        content = listOf(
          Prisoner(
            prisonerNumber = "A1234AA",
            firstName = "Test",
            lastName = "Test",
            prisonId = prisonId,
            prisonName = prisonId,
            cellLocation = locations.first(),
          ),
        ),
      )
    }

    stubFor(
      post(anyUrl())
        .withRequestBody(
          equalToJson(requestBody, true, false),
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsBytes(result))
            .withStatus(200),
        ),
    )
  }
}
