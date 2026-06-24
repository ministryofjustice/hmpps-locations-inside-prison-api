package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LatestOffenderMovement
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.MovementCount
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.OffenderMovement
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonRollMovementInfo
import java.time.LocalDate
import kotlin.collections.emptyList

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8096
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

  fun stubMovementsToday(
    prisonId: String,
    movements: PrisonRollMovementInfo = PrisonRollMovementInfo(inOutMovementsToday = MovementCount(0, 0), enRouteToday = 0),
  ) {
    stubFor(
      get("/api/prison/roll-count/$prisonId/movement-count")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsBytes(movements))
            .withStatus(200),
        ),
    )
  }

  fun stubOffenderMovementsToday(
    prisonId: String,
    date: LocalDate,
    offenderMovements: List<OffenderMovement> = emptyList(),
  ) {
    stubFor(
      get("/api/movements/$prisonId/out/$date")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsBytes(offenderMovements))
            .withStatus(200),
        ),
    )
  }

  fun stubLatestOffenderMovements(
    latestMovements: List<LatestOffenderMovement> = emptyList(),
    expectedOffenderNumbers: List<String>? = null,
  ) {
    var mappingBuilder = post(urlPathEqualTo("/api/movements/offenders"))
      .withQueryParam("latestOnly", equalTo("true"))

    if (expectedOffenderNumbers != null) {
      mappingBuilder = mappingBuilder.withRequestBody(equalToJson(mapper.writeValueAsString(expectedOffenderNumbers), true, true))
    }

    stubFor(
      mappingBuilder.willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(mapper.writeValueAsBytes(latestMovements))
          .withStatus(200),
      ),
    )
  }
}
