package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.CacheConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonConfigurationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ServiceStatus
import java.time.LocalDateTime

class PrisonConfigurationResourceTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var prisonConfigurationRepository: PrisonConfigurationRepository

  @Autowired
  lateinit var cacheManager: CacheManager

  val prisonId = "LEI"

  @BeforeEach
  fun setUp() {
    prisonConfigurationRepository.save(
      PrisonConfiguration(
        id = prisonId,
        whenUpdated = LocalDateTime.now(clock),
        updatedBy = "TEST",
      ),
    )
  }

  @DisplayName("GET /prison-configuration")
  @Nested
  inner class GetAllPrisonConfigurationsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prison-configuration")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-configuration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-configuration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        cacheManager.getCache(CacheConfiguration.PRISON_NAMES_CACHE_NAME)?.clear()
        prisonConfigurationRepository.save(
          PrisonConfiguration(
            id = "BXI",
            resiLocationServiceActive = true,
            certificationApprovalRequired = true,
            whenUpdated = LocalDateTime.now(clock),
            updatedBy = "TEST",
          ),
        )
      }

      @Test
      fun `lists every prison with its name, sorted by name`() {
        prisonRegisterMockServer.stubGetAllPrisons(
          listOf(
            PrisonDto(prisonId = "LEI", prisonName = "Leeds (HMP)", active = true, male = true, female = false, contracted = false, lthse = false),
            PrisonDto(prisonId = "BXI", prisonName = "Brixton (HMP)", active = true, male = true, female = false, contracted = false, lthse = false),
          ),
        )

        val configurations = webTestClient.get().uri("/prison-configuration")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBodyList(PrisonConfigurationDto::class.java)
          .returnResult().responseBody!!

        assertThat(configurations.map { it.prisonName }).isSortedAccordingTo(compareBy { it!!.lowercase() })
        assertThat(configurations.first { it.prisonId == "BXI" }).isEqualTo(
          PrisonConfigurationDto(
            prisonId = "BXI",
            prisonName = "Brixton (HMP)",
            resiLocationServiceActive = ServiceStatus.ACTIVE,
            includeSegregationInRollCount = ServiceStatus.INACTIVE,
            certificationApprovalRequired = ServiceStatus.ACTIVE,
          ),
        )
        assertThat(configurations.first { it.prisonId == prisonId }.prisonName).isEqualTo("Leeds (HMP)")
      }

      @Test
      fun `falls back to the prison ID when the prison register has no name`() {
        prisonRegisterMockServer.stubGetAllPrisons(emptyList())

        webTestClient.get().uri("/prison-configuration")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$[?(@.prisonId == 'BXI')].prisonName").isEqualTo("BXI")
      }
    }
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
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
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
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
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
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
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
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("PUT /prison-configuration/{prisonId}/non-resi-service/{status}")
  @Nested
  inner class UpdateNonResiServiceStatusTest {

    @Test
    fun `non-resi service status can no longer be changed`() {
      webTestClient.put().uri("/prison-configuration/$prisonId/non-resi-service/INACTIVE")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @DisplayName("PUT /prison-configuration/{prisonId}/certification-approval-required/{status}")
  @Nested
  inner class UpdateCertificationApprovalProcessTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/ACTIVE")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/ACTIVE")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when non existent prison used`() {
        webTestClient.put().uri("/prison-configuration/JJI/certification-approval-required/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid prison used`() {
        webTestClient.put().uri("/prison-configuration/XXXXXX/certification-approval-required/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid status used`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/XXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update certification approval process`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "ACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )

        webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/INACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("PUT /prison-configuration/{prisonId}/include-seg-in-roll-count/{status}")
  @Nested
  inner class UpdateIncludeSegInRollCountTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/ACTIVE")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/ACTIVE")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when non existent prison used`() {
        webTestClient.put().uri("/prison-configuration/JJI/include-seg-in-roll-count/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid prison used`() {
        webTestClient.put().uri("/prison-configuration/XXXXXX/include-seg-in-roll-count/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `error occurs when invalid status used`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/XXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update include seg in roll count status`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "ACTIVE",
                "certificationApprovalRequired": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )

        webTestClient.put().uri("/prison-configuration/$prisonId/include-seg-in-roll-count/INACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "resiLocationServiceActive": "INACTIVE",
                "nonResiServiceActive": "ACTIVE",
                "includeSegregationInRollCount": "INACTIVE",
                "certificationApprovalRequired": "INACTIVE"
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }
}
