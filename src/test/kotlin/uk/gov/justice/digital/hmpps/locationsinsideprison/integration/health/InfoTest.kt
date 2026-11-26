package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class InfoTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var buildProperties: BuildProperties

  @Test
  fun `Info page is accessible`() {
    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.name").isEqualTo("hmpps-locations-inside-prison-api")
  }

  @Test
  fun `Info page reports version`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").isEqualTo(buildProperties.version!!)
  }

  @Test
  fun `Info page no longer lists active agencies`() {
    // Removed in MAPA-466 once Change someone's cell stopped reading it. Residential activation per prison is in
    // prison configuration.
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("activeAgencies").doesNotExist()
  }
}
