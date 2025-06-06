package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisDeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationHistoryRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
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

  @Autowired
  lateinit var linkedTransactionRepository: LinkedTransactionRepository

  lateinit var wingB: ResidentialLocation
  lateinit var landing1: ResidentialLocation

  lateinit var cell: Cell
  lateinit var permDeactivated: Cell
  lateinit var room: ResidentialLocation
  lateinit var nonRes: NonResidentialLocation
  lateinit var locationHistory: LocationHistory
  lateinit var linkedTransaction: LinkedTransaction

  @BeforeEach
  fun setUp() {
    locationHistoryRepository.deleteAll()
    repository.deleteAll()

    linkedTransaction = linkedTransactionRepository.save(
      LinkedTransaction(
        prisonId = "ZZGHI",
        transactionType = TransactionType.LOCATION_CREATE,
        transactionDetail = "Initial Data Load",
        transactionInvokedBy = EXPECTED_USERNAME,
        txStartTime = LocalDateTime.now(clock).minusDays(10),
      ),
    )

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
      certification = Certification(certified = true, certifiedNormalAccommodation = 1),
      residentialAttributeValues = setOf(ResidentialAttributeValue.CAT_A),
      specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      linkedTransaction = linkedTransaction,
    )
    nonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "ZZGHI",
        pathHierarchy = "B-1-VISIT",
        locationType = LocationType.VISITS,
        nonResidentialUsageType = NonResidentialUsageType.VISIT,
      ),
    )
    room = repository.save(
      buildResidentialLocation(
        prisonId = "ZZGHI",
        pathHierarchy = "B-1-012",
        locationType = LocationType.ROOM,
      ),
    )

    permDeactivated = repository.save(
      buildCell(
        prisonId = "ZZGHI",
        pathHierarchy = "B-1-013",
        status = LocationStatus.ARCHIVED,
        linkedTransaction = linkedTransaction,
      ),
    )
    locationHistory = cell.addHistory(
      attributeName = LocationAttribute.LOCAL_NAME,
      oldValue = "2",
      newValue = "1",
      amendedBy = "user",
      amendedDate = LocalDateTime.now(clock).minusYears(2),
      linkedTransaction = linkedTransaction,
    )!!
    cell = repository.save(cell)

    wingB.addChildLocation(landing1)
    landing1.addChildLocation(cell)
    landing1.addChildLocation(permDeactivated)
    landing1.addChildLocation(room)
    landing1.addChildLocation(nonRes)
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

      @Test
      fun `cannot sync an existing permanently deactivated location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = permDeactivated.id,
                code = "013",
                capacity = CapacityDTO(3, 3),
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody().json(
            // language=json
            """ 
                {
                  "status": 409,
                  "userMessage": "Deactivated Location Exception: Location ZZGHI-B-1-013 cannot be updated as has been permanently deactivated",
                  "developerMessage": "Location ZZGHI-B-1-013 cannot be updated as has been permanently deactivated",
                  "errorCode": 107
                }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/sync/id/${permDeactivated.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "013",
              "pathHierarchy": "B-1-013",
              "active": false,
              "permanentlyDeactivated": true
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `cannot sync an a converted cell`() {
        cell.convertToNonResidentialCell(convertedCellType = ConvertedCellType.OFFICE, userOrSystemInContext = "user", clock = clock, linkedTransaction = linkedTransaction)
        cell.updateComments("This comment will NOT change", "user", clock, linkedTransaction)
        repository.save(cell)
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = cell.id,
                comments = "This will not allow this updated comment",
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody().json(
            // language=json
            """ 
                {
                  "status": 409,
                  "errorCode": ${ErrorCode.LocationCannotByUpdatedAsConvertedCell.errorCode},
                  "userMessage": "Location cannot be updated exception: Location ZZGHI-B-1-001 cannot be updated as has been converted to non-res cell"
                }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/sync/id/${cell.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "001",
              "pathHierarchy": "B-1-001",
              "active": true,
              "comments": "This comment will NOT change"
              }
          """,
            JsonCompareMode.LENIENT,
          )
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
            JsonCompareMode.LENIENT,
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
            JsonCompareMode.LENIENT,
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
                certification = CertificationDTO(certified = false, capacityOfCertifiedCell = 0),
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
              "ignoreWorkingCapacity": false,
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 3
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can sync an existing res location and update it the location type from cell to room`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = cell.id,
                code = "001",
                locationType = LocationType.ROOM,
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
              "locationType": "ROOM",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-001",
              "orderWithinParentLocation": 1,
              "ignoreWorkingCapacity": true,
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can sync an existing res location and update it to a non-res location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = cell.id,
                code = "VISIT-ROOM",
                locationType = LocationType.VISITS,
                residentialHousingType = null,
                usage = setOf(NonResidentialUsageDto(usageType = NonResidentialUsageType.VISIT)),
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
              "code": "VISIT-ROOM",
              "pathHierarchy": "B-1-VISIT-ROOM",
              "locationType": "VISITS",
              "active": true,
              "key": "ZZGHI-B-1-VISIT-ROOM",
              "orderWithinParentLocation": 1,
              "usage": [
                {
                  "usageType": "VISIT",
                  "sequence": 99
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can sync an existing room res location and update it the location type from room to cell`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = room.id,
                code = "012",
                locationType = LocationType.CELL,
                capacity = CapacityDTO(1, 1),
                certification = CertificationDTO(certified = true, capacityOfCertifiedCell = 1),
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
              "code": "012",
              "pathHierarchy": "B-1-012",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-012",
              "orderWithinParentLocation": 1,
              "ignoreWorkingCapacity": false,
              "capacity": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 1
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${room.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "012",
              "pathHierarchy": "B-1-012",
              "locationType": "CELL",
              "accommodationTypes": [ 
                "NORMAL_ACCOMMODATION"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "active": true,
              "key": "ZZGHI-B-1-012",
              "capacity": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 1
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can sync an existing non res location and update to cell`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              syncResRequest.copy(
                id = nonRes.id,
                code = "005",
                parentLocationPath = "B-1",
                residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
                locationType = LocationType.CELL,
                capacity = CapacityDTO(1, 1),
                certification = CertificationDTO(certified = true, capacityOfCertifiedCell = 1),
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
              "code": "005",
              "pathHierarchy": "B-1-005",
              "locationType": "CELL",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "ZZGHI-B-1-005",
              "orderWithinParentLocation": 1,
              "ignoreWorkingCapacity": false,
              "capacity": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 1
              }
            }
          """,
            JsonCompareMode.LENIENT,
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
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can sync a new CSWAP location`() {
        webTestClient.post().uri("/sync/upsert")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              NomisSyncLocationRequest(
                prisonId = "ZZGHI",
                code = "CSWAP",
                locationType = LocationType.HOLDING_AREA,
                residentialHousingType = ResidentialHousingType.OTHER_USE,
                localName = "Cell Swap",
                comments = "Dummy Cell Swap",
                orderWithinParentLocation = 1,
                lastUpdatedBy = "user",
                capacity = CapacityDTO(99, 99),
              ),
            ),
          )
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "ZZGHI",
              "code": "CSWAP",
              "pathHierarchy": "CSWAP",
              "locationType": "HOLDING_AREA",
              "active": true,
              "key": "ZZGHI-CSWAP",
              "comments": "Dummy Cell Swap",
              "localName": "Cell Swap",
              "orderWithinParentLocation": 1,
              "capacity": {
                "workingCapacity": 99,
                "maxCapacity": 99
              }
            }
          """,
            JsonCompareMode.LENIENT,
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
            JsonCompareMode.LENIENT,
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

      @Test
      fun `cannot delete an existing location which as children`() {
        webTestClient.delete().uri("/sync/delete/${wingB.id}")
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

  @DisplayName("GET /sync/id/{id}")
  @Nested
  inner class SyncNomisToLocation {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/sync/id/${UUID.randomUUID()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/sync/id/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/sync/id/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.get().uri("/sync/id/-23rf-$$££@")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `handles location not found`() {
        webTestClient.get().uri("/sync/id/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_SYNC_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve a cell by id`() {
        val legacyLocation = webTestClient.get().uri("/sync/id/${cell.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody(LegacyLocation::class.java)
          .returnResult().responseBody!!

        Assertions.assertEquals(cell.prisonId, legacyLocation.prisonId)
        Assertions.assertEquals(cell.id, legacyLocation.id)
        Assertions.assertEquals(cell.getPathHierarchy(), legacyLocation.pathHierarchy)
        Assertions.assertFalse(legacyLocation.ignoreWorkingCapacity)
      }
    }

    @Test
    fun `can retrieve a wing by id`() {
      val legacyLocation = webTestClient.get().uri("/sync/id/${wingB.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody(LegacyLocation::class.java)
        .returnResult().responseBody!!

      Assertions.assertEquals(wingB.prisonId, legacyLocation.prisonId)
      Assertions.assertEquals(wingB.id, legacyLocation.id)
      Assertions.assertEquals(wingB.getPathHierarchy(), legacyLocation.pathHierarchy)
      Assertions.assertTrue(legacyLocation.ignoreWorkingCapacity)
    }
  }
}
