package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class OperationalCapacityGetIntTest : SqsIntegrationTestBase() {

  @DisplayName("GET /signed-op-cap/MDI")
  @Nested
  inner class ViewLocationsConstantsTest {

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
    inner class HappyPath {
      @Test
      fun `can retrieve location-type constants`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "signedOperationCapacity": 342,
                "approvedBy": "MWING"
              }
            """.trimIndent(),
            false,
          )
      }
    }
  }
}
