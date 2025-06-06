package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository

class SignedOperationCapacityResourceTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: PrisonConfigurationRepository

  @AfterEach
  fun cleanUp() {
    prisonRegisterMockServer.resetAll()
    repository.deleteAll()
  }

  @DisplayName("POST /signed-op-cap/")
  @Nested
  inner class PrisonConfigurationPostIntTest {

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
                "signedOperationCapacity": "10",
                "updatedBy": "TEST"
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
                "signedOperationCapacity": "10",
                "updatedBy": "TEST"
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
      @Sql("classpath:repository/insert-dummy-locations.sql")
      fun `prisonId not found`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "XYZ",
                "signedOperationCapacity": "10",
                "updatedBy": "TEST"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `bad request when missing prisonId`() {
        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "",
                "signedOperationCapacity": "10",
                "updatedBy": "TEST"
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
                "updatedBy": "TEST"
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
                "signedOperationCapacity": "10",
                "updatedBy": "TEST"
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
                "signedOperationCapacity": "10",
                "updatedBy": ""
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      @Sql("classpath:repository/insert-dummy-locations.sql")
      fun `cannot update Signed Operation Capacity when more than max capacity`() {
        prisonRegisterMockServer.stubLookupPrison("MDI")

        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 12,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      @Sql("classpath:repository/insert-dummy-locations.sql")
      fun `can create Signed Operation Capacity`() {
        prisonRegisterMockServer.stubLookupPrison("HLI")

        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "HLI",
                "signedOperationCapacity": 0,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            """
              { 
                "signedOperationCapacity": 0,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.signed-op-cap.amended" to "HLI",
          )
        }
      }

      @Test
      @Sql("classpath:repository/insert-dummy-locations.sql")
      fun `can update Signed Operation Capacity`() {
        prisonRegisterMockServer.stubLookupPrison("MDI")

        webTestClient.post().uri("/signed-op-cap/")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
              { 
                "prisonId": "MDI",
                "signedOperationCapacity": 7,
                "updatedBy": "TEST"
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
                "signedOperationCapacity": 11,
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
                "signedOperationCapacity": 11,
                "updatedBy": "TEST"
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
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
