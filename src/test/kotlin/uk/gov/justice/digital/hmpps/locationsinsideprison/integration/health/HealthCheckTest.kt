package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class HealthCheckTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var buildProperties: BuildProperties

  @Test
  fun `Health page reports ok`() {
    stubPings()

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `Health info reports version`() {
    stubPings()

    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("components.healthInfo.details.version").isEqualTo(buildProperties.version!!)
  }

  private fun stubPings() {
    hmppsAuthMockServer.stubHealthPing(200)
    prisonerSearchMockServer.stubHealthPing(200)
    prisonRegisterMockServer.stubHealthPing(200)
    prisonApiMockServer.stubHealthPing(200)
    manageUsersApiMockServer.stubHealthPing(200)
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }
}
