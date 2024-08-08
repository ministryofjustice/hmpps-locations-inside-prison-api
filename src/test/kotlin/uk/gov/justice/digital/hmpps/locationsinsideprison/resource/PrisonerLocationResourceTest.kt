package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.Clock

@WithMockAuthUser(username = EXPECTED_USERNAME)
class PrisonerLocationResourceTest : CommonDataTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @DisplayName("GET /prisoner-locations/id/{id}")
  @Nested
  inner class PrisonerLocations {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prisoner-locations/id/${wingZ.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-locations/id/${cell1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-locations/id/${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    //TODO wing or cell don't return data
    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location by key`() {
        val result = webTestClient.get().uri("/locations/key/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """   {
    "cellLocation": "1-1-001",
    "prisoners": [
      {
        "prisonerNumber": "A1234AA",
        "prisonId": "LEI",
        "prisonName": "HMP Leeds",
        "cellLocation": "1-1-001",
        "firstName": "Dave",
        "lastName": "Jones",
        "gender": "Male",
        "csra": "High",
        "category": "C",
        "alerts": [
          {
            "alertType": "X",
            "alertCode": "XA",
            "active": true,
            "expired": false
          }
        ]
      }
    ]
  }
          """,
            false,
          )
      }
    }
  }
}
