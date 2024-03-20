package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.MigrateHistoryRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpsertLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import java.time.Clock
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDTO
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification as CertificationDTO

class SyncAndMigrateResourceIntTest : SqsIntegrationTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @Autowired
  lateinit var repository: LocationRepository

  lateinit var wingB: ResidentialLocation
  lateinit var landing1: ResidentialLocation
  lateinit var cell: Cell

  @BeforeEach
  fun setUp() {
    repository.deleteAll()

    wingB = repository.save(
      buildResidentialLocation(
        prisonId = "ZZGHI",
        pathHierarchy = "B",
        locationType = LocationType.WING,
      ),
    )

    landing1 = repository.save(
      buildResidentialLocation(
        prisonId = "ZZGHI",
        pathHierarchy = "B-1",
        locationType = LocationType.LANDING,
      ),
    )

    cell = repository.save(
      buildCell(
        prisonId = "ZZGHI",
        pathHierarchy = "B-1-001",
        capacity = Capacity(capacity = 2, operationalCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 1),
        residentialAttributeValues = setOf(ResidentialAttributeValue.CAT_A),
      ),
    )

    wingB.addChildLocation(landing1)
    landing1.addChildLocation(cell)
    repository.save(wingB)
  }

  @DisplayName("POST /sync/upsert")
  @Nested
  inner class CreateLocationTest {
    var syncResRequest = UpsertLocationRequest(
      prisonId = "ZZGHI",
      code = "003",
      locationType = LocationType.CELL,
      description = "A New Cell",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(clock).minusYears(1),
      attributes = setOf(ResidentialAttributeValue.IMMIGRATION_DETAINEES),
    )

    var syncNonResRequest = UpsertLocationRequest(
      prisonId = "ZZGHI",
      code = "VISIT",
      locationType = LocationType.VISITS,
      description = "Visit Hall",
      comments = "This is a visit room",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
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
      fun `can sync a new res location with parent UUID`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest.copy(parentId = landing1.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "003",
              "pathHierarchy": "B-1-003",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-003",
              "comments": "This is a new cell",
              "description": "A New Cell",
              "orderWithinParentLocation": 1,
              "isResidential": true,
              "attributes": ["IMMIGRATION_DETAINEES"]
            }
          """,
            false,
          )
      }

      @Test
      fun `can sync a new res location with parent code`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest.copy(parentLocationPath = "B-1")))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "003",
              "pathHierarchy": "B-1-003",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-003",
              "comments": "This is a new cell",
              "description": "A New Cell",
              "orderWithinParentLocation": 1,
              "isResidential": true,
              "attributes": ["IMMIGRATION_DETAINEES"]
            }
          """,
            false,
          )
      }

      @Test
      fun `can sync a new res location and update it`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(syncResRequest.copy(id = cell.id, code = "001", attributes = setOf(ResidentialAttributeValue.CAT_A))))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "001",
              "pathHierarchy": "B-1-001",
              "locationType": "CELL",
              "description": "A New Cell",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-001",
              "orderWithinParentLocation": 1,
              "isResidential": true,
              "attributes": [
                "CAT_A"
              ]
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
              "prisonId": "ZZGHI",
              "code": "VISIT",
              "pathHierarchy": "VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "ZZGHI-VISIT",
              "comments": "This is a visit room",
              "description": "Visit Hall",
              "orderWithinParentLocation": 1,
              "isResidential": false,
              "usage": [
                {
                  "usageType": "VISIT",
                  "capacity": 10,
                  "sequence": 1
                }
              ]
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("POST /migrate/location")
  @Nested
  inner class MigrateLocationTest {
    var migrateRequest = UpsertLocationRequest(
      prisonId = "ZZGHI",
      code = "002",
      locationType = LocationType.CELL,
      description = "A New Cell",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      parentLocationPath = "B-1",
      lastModifiedDate = LocalDateTime.now(clock).minusYears(2),
      capacity = CapacityDTO(1, 1),
      certification = CertificationDTO(true, 1),
      attributes = setOf(ResidentialAttributeValue.CAT_B),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/migrate/location")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/location")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/location")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/migrate/location")
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
        webTestClient.post().uri("/migrate/location")
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
        webTestClient.post().uri("/migrate/location")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "002",
              "pathHierarchy": "B-1-002",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-002",
              "comments": "This is a new cell",
              "description": "A New Cell",
              "orderWithinParentLocation": 1,
              "capacity": {
                "capacity": 1,
                "operationalCapacity": 1
              },
              "attributes": ["CAT_B"]
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("POST /migrate/location/{id}/history")
  @Nested
  inner class MigrateLocationHistoryTest {
    var migrateHistoryRequest = MigrateHistoryRequest(
      attribute = LocationAttribute.CAPACITY,
      oldValue = "2",
      newValue = "1",
      amendedBy = "user",
      amendedDate = LocalDateTime.now(clock).minusYears(2),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateHistoryRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateHistoryRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateHistoryRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"attribute": "SOME_TEXT"}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can migrate a location history`() {
        webTestClient.post().uri("/migrate/location/${wingB.id}/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateHistoryRequest))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "attribute": "Capacity",
              "oldValue": "2",
              "newValue": "1",
              "amendedBy": "user",
              "amendedDate": "${migrateHistoryRequest.amendedDate}"
            }
          """,
            false,
          )
      }
    }
  }
}
