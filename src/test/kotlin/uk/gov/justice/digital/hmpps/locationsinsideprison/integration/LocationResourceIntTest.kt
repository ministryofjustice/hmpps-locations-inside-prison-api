package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest

private const val LOCATION_ID = "de91dfa7-821f-4552-a427-bf2f32eafeb0"

class LocationResourceIntTest : SqsIntegrationTestBase() {
  @DisplayName("GET /locations")
  @Nested
  inner class ViewLocationTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/$LOCATION_ID")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/$LOCATION_ID")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/$LOCATION_ID")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve details of a location`() {
        webTestClient.get().uri("/locations/$LOCATION_ID")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
      }
    }
  }

  @DisplayName("POST /locations")
  @Nested
  inner class CreateLocationTest {
    val createLocationRequest = CreateLocationRequest(name = "A-2-002")

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"name": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody()
      }
    }
  }
}
