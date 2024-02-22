package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpsertLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import java.time.Clock
import java.time.LocalDateTime

class SyncAndMigrateResourceIntTest : SqsIntegrationTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @DisplayName("POST /sync/upsert")
  @Nested
  inner class CreateLocationTest {
    var syncResRequest = UpsertLocationRequest(
      prisonId = "XXY",
      code = "A",
      locationType = LocationType.WING,
      description = "A New Wing",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new wing",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(),
      attributes = setOf(ResidentialAttributeValue.IMMIGRATION_DETAINEES),
    )

    var syncNonResRequest = UpsertLocationRequest(
      prisonId = "XXY",
      code = "VISIT",
      locationType = LocationType.VISITS,
      description = "Visit Hall",
      comments = "This is a visit room",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(),
      usage = setOf(NonResidentialUsageDto(NonResidentialUsageType.VISIT, 10, 1)),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/sync/upsert")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can sync a new res location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "XXY",
              "code": "A",
              "pathHierarchy": "A",
              "locationType": "WING",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "XXY-A",
              "comments": "This is a new wing",
              "description": "A New Wing",
              "orderWithinParentLocation": 1,
              "isResidential": true
            }
          """,
            false,
          )
      }

      @Test
      fun `can sync a new non-res location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncNonResRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "XXY",
              "code": "VISIT",
              "pathHierarchy": "VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "XXY-VISIT",
              "comments": "This is a visit room",
              "description": "Visit Hall",
              "orderWithinParentLocation": 1,
              "isResidential": false
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("POST /migrate")
  @Nested
  inner class MigrateLocationTest {
    var migrateRequest = UpsertLocationRequest(
      prisonId = "XXY",
      code = "001",
      locationType = LocationType.CELL,
      description = "A New Cell",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(),
      capacity = Capacity(1, 1),
      certification = Certification(true, 1),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/migrate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/migrate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/migrate")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can migrate a location`() {
        webTestClient.post().uri("/migrate")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "XXY",
              "code": "001",
              "pathHierarchy": "001",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "XXY-001",
              "comments": "This is a new cell",
              "description": "A New Cell",
              "orderWithinParentLocation": 1,
              "capacity": {
                "capacity": 1,
                "operationalCapacity": 1
              }
            }
          """,
            false,
          )
      }
    }
  }
}
