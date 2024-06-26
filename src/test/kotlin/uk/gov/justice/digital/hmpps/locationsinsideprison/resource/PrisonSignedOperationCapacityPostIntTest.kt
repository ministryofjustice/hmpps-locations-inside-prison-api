package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository

class PrisonSignedOperationCapacityPostIntTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: PrisonSignedOperationCapacityRepository

  @AfterEach
  fun cleanUp() {
    repository.deleteAll()
  }

  @DisplayName("POST /signed-op-cap/")
  @Nested
  inner class PrisonSignedOperationCapacityPostIntTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/signed-op-cap/")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": "100",
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": "100",
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `bad request when missing prisonId`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "",
                "signedOperationCapacity": "100",
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad request when signedOperationCapacity less than 0`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": -1,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad post request when PrisonId is not valid`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "X6_XXX",
                "signedOperationCapacity": "100",
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad post request when signed op cap -1`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
                { 
                  "prisonId": "MDI",
                  "signedOperationCapacity": -1,
                  "updatedBy": "MALEMAN"
                }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad request when user who make a changes is not provided`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": "100",
                "updatedBy": ""
              }
            """.trimIndent(),
          )
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
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 100,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
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

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.signed-op-cap.amended" to "MDI",
          )
        }
      }

      @Test
      fun `can update Signed Operation Capacity`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 100,
                "updatedBy": "MALEMAN"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isCreated

        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 200,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 200,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
            false,
          )

        getDomainEvents(2).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.signed-op-cap.amended" to "MDI",
            "location.inside.prison.signed-op-cap.amended" to "MDI",
          )
        }
      }
    }
  }
}
