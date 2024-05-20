package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class OperationalCapacityPostIntTest : SqsIntegrationTestBase() {

  @DisplayName("POST /signed-op-cap/MDI")
  @Nested
  inner class ViewLocationsConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/signed-op-cap/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve location-type constants`() {
        webTestClient.post().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            """
              {
                "signedOperationCapacity": 100,
                "approvedBy": "MALEMAN"
              }
            """.trimIndent(),
            false,
          )
      }
    }
  }
}
