package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class PrisonSignedOperationCapacityPostIntTest : SqsIntegrationTestBase() {

  @DisplayName("POST /signed-op-cap/MDI")
  @Nested
  inner class PrisonSignedOperationCapacityPostIntTest {

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
    inner class Validation {

      @Test
      fun `bad request missing PrisonId`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""
              { 
                "prisonId": "",
                "signedOperationCapacity": "100",
                "updatedBy": "MALEMAN"
              }
            """.trimIndent())
          .exchange()
          .expectStatus().is4xxClientError
      }

    //todo
    fun `post error return bad data -1`() {
      webTestClient.post().uri("/signed-op-cap/")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue("""
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": -1,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent())
        .exchange()
        .expectStatus().is4xxClientError
    }
  }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create Signed Operation Capacity`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 100,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent())
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            """
              { 
                "signedOperationCapacity": 100,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
            false,
          )
      }
    }
  }
}
