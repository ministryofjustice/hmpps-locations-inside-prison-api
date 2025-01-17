package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.ActivePrisonConfig
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
@DisplayName("GET /locations/{id}/attributes")
class CellAttributesResourceIntTest : CommonDataTestBase() {

  @MockitoBean
  private lateinit var activePrisonConfig: ActivePrisonConfig

  @BeforeEach
  fun beforeEach() {
    whenever(activePrisonConfig.isActivePrison("MDI")).thenReturn(true)
    whenever(activePrisonConfig.isActivePrison("NMI")).thenReturn(false)
  }

  @Nested
  inner class Security {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/locations/${cell1.id}/attributes")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/locations/${cell1.id}/attributes")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/locations/${cell1.id}/attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class Validation {

    @Test
    fun `cannot get attributes if id is not valid`() {
      webTestClient.get().uri("/locations/12345/attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().is4xxClientError
    }
  }

  @Nested
  inner class HappyPath {

    @Test
    fun `can get legacy residential attributes for cell`() {

      webTestClient.get().uri("/locations/${cell1N.id}/attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
                          [
                            {"code":"DOUBLE_OCCUPANCY","description":"Double Occupancy"}
                          ]
                        """,
          JsonCompareMode.STRICT,
        )
    }


    @Test
    fun `can get specialist attributes for cell`() {
      webTestClient.get().uri("/locations/${cell2.id}/attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
                          [{"code":"ACCESSIBLE_CELL","description":"Accessible cell"}]
                        """,
          JsonCompareMode.STRICT,
        )
    }
  }

}

