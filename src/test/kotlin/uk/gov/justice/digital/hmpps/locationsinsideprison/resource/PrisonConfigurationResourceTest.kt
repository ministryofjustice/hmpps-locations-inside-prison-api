package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import java.time.LocalDateTime

class PrisonConfigurationResourceTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var prisonConfigurationRepository: PrisonConfigurationRepository

  val prisonId = "MDI"

  @BeforeEach
  fun setUp() {
    prisonConfigurationRepository.save(
      PrisonConfiguration(
        prisonId = prisonId,
        signedOperationCapacity = 0,
        whenUpdated = LocalDateTime.now(clock),
        updatedBy = "TEST",
      ),
    )
  }

  @DisplayName("GET /prison-configuration/{prisonId}")
  @Nested
  inner class GetPrisonConfigurationTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prison-configuration/$prisonId")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-configuration/$prisonId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-configuration/$prisonId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when non existent prison used`() {
        webTestClient.get().uri("/prison-configuration/JJI")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid prison used`() {
        webTestClient.get().uri("/prison-configuration/XXXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can get prison configuration`() {
        webTestClient.get().uri("/prison-configuration/$prisonId")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "includeSegregationInRollCount": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("PUT /prison-configuration/{prisonId}/resi-service/{status}")
  @Nested
  inner class UpdateResiServiceStatusTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/ACTIVE")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/ACTIVE")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when non existent prison used`() {
        webTestClient.put().uri("/prison-configuration/JJI/resi-service/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid prison used`() {
        webTestClient.put().uri("/prison-configuration/XXXXXX/resi-service/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid status used`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/XXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update prison configuration to active`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )

        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/INACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "includeSegregationInRollCount": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `can update prison configuration to inactive`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/resi-service/INACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "includeSegregationInRollCount": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }
}
