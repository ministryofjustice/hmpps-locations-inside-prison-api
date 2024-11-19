package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
              Matcher(
                attribute = "cellLocation",
                condition = "IN",
                searchTerm = locations.distinct().sorted().joinToString(","),
              ),
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
        content = locations.mapIndexed { index, location ->
          createPrisoner(
            prisonId = prisonId,
            cellLocation = location,
            index = index,
          )
        },
      )
    }

    stubFor(
      post(urlPathMatching("/attribute-search"))
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

  fun stubAllPrisonersInPrison(
    prisonId: String,
  ) {
    val result = SearchResult(
      content = listOf(
        createPrisoner(prisonId = prisonId, cellLocation = "Z-1-001", index = 1000, inOutStatus = "IN", status = "ACTIVE IN"),
        createPrisoner(prisonId = prisonId, cellLocation = "Z-1-002", index = 1001, inOutStatus = "OUT", status = "ACTIVE OUT"),
        createPrisoner(prisonId = prisonId, cellLocation = "Z-1-003", index = 1002, inOutStatus = "IN", status = "ACTIVE IN"),
        createPrisoner(prisonId = prisonId, cellLocation = "RECP", index = 1003, inOutStatus = "IN", status = "ACTIVE IN"),
        createPrisoner(prisonId = prisonId, cellLocation = "RECP", index = 1004, inOutStatus = "IN", status = "ACTIVE IN"),
        createPrisoner(prisonId = prisonId, cellLocation = "RECP", index = 1005, inOutStatus = "OUT", status = "ACTIVE OUT"),
        createPrisoner(prisonId = prisonId, cellLocation = "CSWAP", index = 1006, inOutStatus = "IN", status = "ACTIVE IN"),
        createPrisoner(prisonId = prisonId, cellLocation = "CSWAP", index = 1007, inOutStatus = "OUT", status = "ACTIVE OUT"),
      ),
    )

    stubFor(
      get(urlPathMatching("/prisoner-search/prison/$prisonId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsBytes(result))
            .withStatus(200),
        ),
    )
  }
}

fun createPrisoner(prisonId: String, cellLocation: String, index: Int, status: String = "ACTIVE IN", inOutStatus: String = "IN") =
  Prisoner(
    prisonerNumber = "A${index.toString().padStart(4, '0')}AA",
    firstName = "Firstname-$index",
    lastName = "Surname-$index",
    prisonId = prisonId,
    prisonName = prisonId,
    cellLocation = cellLocation,
    gender = "MALE",
    status = status,
    lastMovementTypeCode = "ADM",
    inOutStatus = inOutStatus,
    csra = "High",
    category = "C",
    alerts = emptyList(),
  )
