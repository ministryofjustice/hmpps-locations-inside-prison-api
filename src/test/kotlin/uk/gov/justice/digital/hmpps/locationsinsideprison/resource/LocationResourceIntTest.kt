package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

const val expectedUsername = "A_TEST_USER"

@WithMockUser(username = expectedUsername)
class LocationResourceIntTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: LocationRepository
  lateinit var location: LocationJPA

  @BeforeEach
  fun setUp() {
    repository.deleteAll()

    var wing = repository.save(buildLocation(code = "Z", locationType = LocationType.WING))
    val landing = repository.save(buildLocation(code = "Z-1", locationType = LocationType.LANDING))
    val cell1 = repository.save(buildLocation(code = "Z-1-001"))
    val cell2 = repository.save(buildLocation(code = "Z-1-002"))
    wing.addChildLocation(landing.addChildLocation(cell1).addChildLocation(cell2))
    wing = repository.save(wing)
    location = cell1
  }

  private fun buildLocation(
    prisonId: String = "MDI",
    code: String,
    locationType: LocationType = LocationType.CELL,
  ) = LocationJPA(
    prisonId = prisonId,
    code = code,
    description = "$prisonId-$code",
    locationType = locationType,
    updatedBy = expectedUsername,
    whenCreated = LocalDateTime.now(clock),
    whenUpdated = LocalDateTime.now(clock),
  )

  @DisplayName("GET /locations")
  @Nested
  inner class ViewLocationTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/${location.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/${location.id}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/${location.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location`() {
        webTestClient.get().uri("/locations/${location.id}")
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
    val createLocationRequest = CreateLocationRequest(
      prisonId = "MDI",
      code = "A",
      description = "MDI A Wing",
      locationType = LocationType.WING,
    )

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
          .bodyValue("""{"code": ""}""")
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
