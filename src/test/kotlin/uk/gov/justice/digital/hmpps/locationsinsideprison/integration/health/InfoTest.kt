package uk.gov.justice.digital.hmpps.locationsinsideprison.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class InfoTest : SqsIntegrationTestBase() {

  @MockitoBean
  private lateinit var prisonConfigurationRepository: PrisonConfigurationRepository

  @BeforeEach
  fun beforeEach() {
    whenever(prisonConfigurationRepository.findAll()).thenReturn(
      listOf(
        PrisonConfiguration(
          id = "MDI",
          resiLocationServiceActive = true,
          whenUpdated = LocalDateTime.now(clock),
          updatedBy = "TEST",
        ),
      ),
    )
  }

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
      .expectBody().jsonPath("build.version").value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
      }
  }

  @Test
  fun `Info page reports active agencies`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("activeAgencies").value<List<String>> {
        assertThat(it).hasSize(1)
        assertThat(it[0]).isEqualTo("***")
      }
  }
}
