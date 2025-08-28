package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository

class PrisonConfigurationGetIntTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: PrisonConfigurationRepository

  @DisplayName("GET /signed-op-cap/MDI")
  @Nested
  inner class PrisonConfigurationGetIntTest {

    @AfterEach
    fun cleanUp() {
      repository.deleteAll()
    }

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `bad GET error response ERROR when URL is wrong`() {
        webTestClient.get().uri("/XXX/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad GET error response ERROR when prisonID doesn't exist`() {
        webTestClient.get().uri("/signed-op-cap/XXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad GET error response ERROR when prisonID is not valid`() {
        webTestClient.get().uri("/signed-op-cap/XXXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve Signed Operation Capacity`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "signedOperationCapacity": 200,
                "updatedBy": "LOCATIONS_INSIDE_PRISON_API",
                "prisonId": "MDI",
                "whenUpdated": "2023-12-05T12:34:56"
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can't retrieve Signed Operation Capacity`() {
        webTestClient.get().uri("/signed-op-cap/XXX")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }
}
