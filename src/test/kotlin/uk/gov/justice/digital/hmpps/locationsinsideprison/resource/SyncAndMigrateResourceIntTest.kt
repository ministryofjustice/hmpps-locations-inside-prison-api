package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisDeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisMigrateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationHistoryRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrNull
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

  @Autowired
  lateinit var locationHistoryRepository: LocationHistoryRepository

  lateinit var wingB: ResidentialLocation
  lateinit var landing1: ResidentialLocation
  lateinit var cell: Cell
  lateinit var locationHistory: LocationHistory

  @BeforeEach
  fun setUp() {
    locationHistoryRepository.deleteAll()
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

    cell = buildCell(
      prisonId = "ZZGHI",
      pathHierarchy = "B-1-001",
      capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
      certification = Certification(certified = true, capacityOfCertifiedCell = 1),
      residentialAttributeValues = setOf(ResidentialAttributeValue.CAT_A),
      specialistCellType = SpecialistCellType.WHEELCHAIR_ACCESSIBLE,
    )
    locationHistory = cell.addHistory(
      attributeName = LocationAttribute.DESCRIPTION,
      oldValue = "2",
      newValue = "1",
      amendedBy = "user",
      amendedDate = LocalDateTime.now(clock).minusYears(2),
    )!!
    cell = repository.save(cell)

    wingB.addChildLocation(landing1)
    landing1.addChildLocation(cell)
    repository.save(wingB)
  }

  @DisplayName("POST /sync/upsert")
  @Nested
  inner class CreateLocationTest {
    var syncResRequest = NomisSyncLocationRequest(
      prisonId = "ZZGHI",
      code = "003",
      locationType = LocationType.CELL,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(clock).minusYears(1),
      attributes = setOf(ResidentialAttributeValue.IMMIGRATION_DETAINEES),
    )

    var syncNonResRequest = NomisSyncLocationRequest(
      prisonId = "ZZGHI",
      code = "VISIT",
      locationType = LocationType.VISITS,
      localName = "Visit Hall",
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
              "orderWithinParentLocation": 1,
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
              "orderWithinParentLocation": 1,
              "attributes": ["IMMIGRATION_DETAINEES"]
            }
          """,
            false,
          )
      }

      @Test
      fun `can sync an existing res location and update it`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = cell.id,
                code = "001",
                attributes = setOf(ResidentialAttributeValue.CAT_A),
                capacity = CapacityDTO(3, 3),
                certification = CertificationDTO(false, 0),
              ),
            ),
          )
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
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-001",
              "orderWithinParentLocation": 1,
              "attributes": [
                "CAT_A"
              ],
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 3
              },
              "certification": {
                "certified": false,
                "capacityOfCertifiedCell": 0
              }
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
              "localName": "Visit Hall",
              "orderWithinParentLocation": 1,
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

  @DisplayName("POST /sync/upsert")
  @Nested
  inner class CreateLocationTestDeactivated {
    var deactivatedLocationMigration = NomisSyncLocationRequest(
      prisonId = "ZZGHI",
      code = "006",
      locationType = LocationType.CELL,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell (inactive)",
      deactivatedDate = LocalDateTime.now(clock).minusYears(1).toLocalDate(),
      deactivationReason = NomisDeactivatedReason.DAMAGED,
      proposedReactivationDate = LocalDateTime.now(clock).plusYears(1).toLocalDate(),
      orderWithinParentLocation = 6,
      lastUpdatedBy = "user",
      lastModifiedDate = LocalDateTime.now(clock).minusYears(1),
    )

    @Nested
    inner class HappyPath {
      @Test
      fun `can sync a new inactive res location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(deactivatedLocationMigration.copy(parentId = landing1.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "006",
              "pathHierarchy": "B-1-006",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": false,
              "deactivatedDate": "${deactivatedLocationMigration.deactivatedDate}",
              "deactivatedReason": "DAMAGED",
              "proposedReactivationDate": "${deactivatedLocationMigration.proposedReactivationDate}",
              "key": "ZZGHI-B-1-006",
              "comments": "This is a new cell (inactive)",
              "orderWithinParentLocation": 6
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
    var migrateRequest = NomisMigrateLocationRequest(
      prisonId = "ZZGHI",
      code = "002",
      locationType = LocationType.CELL,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 1,
      lastUpdatedBy = "user",
      parentLocationPath = "B-1",
      deactivationReason = NomisDeactivatedReason.DAMAGED,
      proposedReactivationDate = LocalDateTime.now(clock).plusMonths(1).toLocalDate(),
      lastModifiedDate = LocalDateTime.now(clock).minusYears(2),
      capacity = CapacityDTO(1, 1),
      certification = CertificationDTO(true, 1),
      attributes = setOf(ResidentialAttributeValue.CAT_B),
      history = listOf(
        ChangeHistory(
          attribute = LocationAttribute.DESCRIPTION.name,
          oldValue = null,
          newValue = "A New Cell",
          amendedBy = "user2",
          amendedDate = LocalDateTime.now(clock).minusYears(2),
        ),
        ChangeHistory(
          attribute = LocationAttribute.COMMENTS.name,
          oldValue = "Old comment",
          newValue = "This is a new cell",
          amendedBy = "user1",
          amendedDate = LocalDateTime.now(clock).minusYears(1),
        ),
      ),
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
              "active": false,
              "key": "ZZGHI-B-1-002",
              "comments": "This is a new cell",
              "orderWithinParentLocation": 1,
              "capacity": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "attributes": ["CAT_B"],
              "deactivatedReason": "${migrateRequest.deactivationReason}",
              "proposedReactivationDate": "${migrateRequest.proposedReactivationDate}"
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/key/ZZGHI-B-1-002?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "ZZGHI",
              "code": "002",
              "pathHierarchy": "B-1-002",
              "locationType": "CELL",
              "accommodationTypes": ["NORMAL_ACCOMMODATION"],
              "active": false,
              "key": "ZZGHI-B-1-002",
              "capacity": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "deactivatedReason": "${migrateRequest.deactivationReason}",
              "proposedReactivationDate": "${migrateRequest.proposedReactivationDate}",
              "changeHistory": [
                {
                  "attribute": "Local Name",
                  "newValue": "A New Cell",
                  "amendedBy": "user2",
                  "amendedDate": "${LocalDateTime.now(clock).minusYears(2)}"
                },
                {
                  "attribute": "Comments",
                  "oldValue": "Old comment",
                  "newValue": "This is a new cell",
                  "amendedBy": "user1",
                  "amendedDate": "${LocalDateTime.now(clock).minusYears(1)}"
                },
                {
                  "attribute": "Used For",
                  "newValue": "Standard accommodation",
                  "amendedBy": "user"
                }
              ]
            }
            """,
            false,
          )
      }

      @Test
      fun `can migrate a location can convert`() {
        webTestClient.post().uri("/migrate/location")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(migrateRequest.copy(residentialHousingType = ResidentialHousingType.HOLDING_CELL)))
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
              "residentialHousingType": "HOLDING_CELL",
              "active": false,
              "key": "ZZGHI-B-1-002",
              "comments": "This is a new cell",
              "orderWithinParentLocation": 1,
              "attributes": ["CAT_B"],
              "deactivatedReason": "${migrateRequest.deactivationReason}",
              "proposedReactivationDate": "${migrateRequest.proposedReactivationDate}"
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/key/ZZGHI-B-1-002?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "ZZGHI",
              "code": "002",
              "pathHierarchy": "B-1-002",
              "locationType": "CELL",
              "accommodationTypes": [
                "OTHER_NON_RESIDENTIAL"
              ],
              "convertedCellType": "HOLDING_ROOM",
              "active": false,
              "key": "ZZGHI-B-1-002",
              "deactivatedReason": "${migrateRequest.deactivationReason}",
              "proposedReactivationDate": "${migrateRequest.proposedReactivationDate}",
              "changeHistory": [
                {
                  "attribute": "Local Name",
                  "newValue": "A New Cell",
                  "amendedBy": "user2",
                  "amendedDate": "${LocalDateTime.now(clock).minusYears(2)}"
                },
                {
                  "attribute": "Comments",
                  "oldValue": "Old comment",
                  "newValue": "This is a new cell",
                  "amendedBy": "user1",
                  "amendedDate": "${LocalDateTime.now(clock).minusYears(1)}"
                },
                {
                  "attribute": "Working Capacity",
                  "oldValue": "1",
                  "amendedBy": "user"
                },
                {
                  "attribute": "Max Capacity",
                  "oldValue": "1",
                  "amendedBy": "user"
                },
                {
                  "attribute": "Baseline Certified Capacity",
                  "oldValue": "1",
                  "newValue": "0",
                  "amendedBy": "user"
                },
                {
                  "attribute": "Certified",
                  "oldValue": "true",
                  "newValue": "false",
                  "amendedBy": "user"
                },
                {
                  "attribute": "Converted Cell Type",
                  "newValue": "Holding room",
                  "amendedBy": "user"
                },
                {
                  "attribute": "Used For",
                  "newValue": "Standard accommodation",
                  "amendedBy": "user"
                }
              ]
            }
            """,
            false,
          )
      }
    }
  }

  @DisplayName("DELETE /sync/delete")
  @Nested
  inner class DeleteLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.delete().uri("/sync/delete/${UUID.randomUUID()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/sync/delete/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/sync/delete/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.delete().uri("/sync/delete/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.delete().uri("/sync/delete/-23rf-$$££@")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `handles location not found`() {
        webTestClient.delete().uri("/sync/delete/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can delete an existing location`() {
        webTestClient.delete().uri("/sync/delete/${cell.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isNoContent

        assertThat(repository.findById(cell.id!!).getOrNull()).isNull()

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deleted" to "ZZGHI-B-1-001",
          )
        }
      }
    }
  }
}
