package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation as ResidentialLocationJPA

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationCapacityResourceIntTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @Autowired
  lateinit var linkedTransactionRepository: LinkedTransactionRepository
  lateinit var cell1: Cell
  lateinit var cell2: Cell
  lateinit var cell3: Cell
  lateinit var cell4: Cell
  lateinit var landingZ1: ResidentialLocationJPA
  lateinit var landingZ2: ResidentialLocationJPA
  lateinit var wingZ: ResidentialLocationJPA
  lateinit var linkedTransaction: LinkedTransaction

  @BeforeEach
  fun setUp() {
    prisonerSearchMockServer.resetAll()
    repository.deleteAll()

    linkedTransaction = linkedTransactionRepository.saveAndFlush(
      LinkedTransaction(
        prisonId = "MDI",
        transactionType = TransactionType.LOCATION_CREATE,
        transactionDetail = "Initial Data Load",
        transactionInvokedBy = EXPECTED_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      ),
    )
    wingZ = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z",
        locationType = LocationType.WING,
      ),
    )
    landingZ1 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-1",
        locationType = LocationType.LANDING,
      ),
    )
    landingZ2 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-2",
        locationType = LocationType.LANDING,
      ),
    )
    cell1 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
        certifiedCell = true,
        specialistCellType = SpecialistCellType.LOCATE_FLAT_CELL,
        linkedTransaction = linkedTransaction,
      ),
    )
    cell2 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-002",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
        certifiedCell = true,
        specialistCellType = SpecialistCellType.LISTENER_CRISIS,
        linkedTransaction = linkedTransaction,
      ),
    )
    cell3 = repository.save(
      buildCell(
        pathHierarchy = "Z-2-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 0, certifiedNormalAccommodation = 1),
        certifiedCell = true,
        specialistCellType = SpecialistCellType.SAFE_CELL,
        linkedTransaction = linkedTransaction,
      ),
    )
    cell4 = repository.save(
      buildCell(
        pathHierarchy = "Z-2-002",
        capacity = Capacity(maxCapacity = 1, workingCapacity = 0, certifiedNormalAccommodation = 0),
        certifiedCell = false,
        linkedTransaction = linkedTransaction,
      ),
    )
    wingZ.addChildLocation(
      landingZ1
        .addChildLocation(cell1)
        .addChildLocation(cell2),
    ).addChildLocation(
      landingZ2
        .addChildLocation(cell3)
        .addChildLocation(cell4),
    )
    repository.save(wingZ)
  }

  @DisplayName("GET /location-occupancy/cells-with-capacity/{prisonId}")
  @Nested
  inner class CellsWithCapacityTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}")
          .header("Content-Type", "application/json")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad prison ID`() {
        webTestClient.get().uri("/location-occupancy/cells-with-capacity/HFIFHOHF")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `access client error bad specialist cell type`() {
        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?specialistCellType=BLOB")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can view cells with capacity`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell1.prisonId,
          listOf(
            cell1.getPathHierarchy(),
            cell2.getPathHierarchy(),
            cell2.getPathHierarchy(),
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                   {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can view cells with capacity filtered by specialistCellType`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell1.prisonId,
          listOf(
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?specialistCellType=SAFE_CELL")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                  {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }                          
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can view cells with capacity from sub location`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell3.prisonId,
          listOf(
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?locationId=${landingZ2.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                  {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can view cells with capacity using group name from config files`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell3.prisonId,
          listOf(
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?groupName=Z-Wing_Landing 2")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                  {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can view cells with capacity using group name`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell3.prisonId,
          listOf(
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?groupName=${landingZ2.getPathHierarchy()}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                  {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can view cells with capacity and prisoner information`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell1.prisonId,
          listOf(
            cell1.getPathHierarchy(),
            cell2.getPathHierarchy(),
            cell2.getPathHierarchy(),
            cell3.getPathHierarchy(),
          ),
          true,
        )

        webTestClient.get().uri("/location-occupancy/cells-with-capacity/${wingZ.prisonId}?includePrisonerInformation=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
              {
                "id": "${cell3.id}",
                "key": "${cell3.getKey()}",
                "prisonId": "${cell3.prisonId}",
                "pathHierarchy": "${cell3.getPathHierarchy()}",
                "noOfOccupants": 1,
                "maxCapacity": ${cell3.getMaxCapacity()},
                "workingCapacity": ${cell3.getWorkingCapacity()},
                "specialistCellTypes": [
                  {
                     "typeCode": "SAFE_CELL",
                     "typeDescription": "Safe cell"
                  }
                ],
                "legacyAttributes": [
                  {
                    "typeCode": "DOUBLE_OCCUPANCY",
                    "typeDescription": "Double Occupancy"
                  }
                ],
                "prisonersInCell": [
                  {
                    "prisonerNumber": "A0003AA",
                    "firstName": "Firstname-3",
                    "lastName": "Surname-3",
                    "gender": "MALE",
                    "cellLocation": "${cell3.getPathHierarchy()}",
                    "prisonId": "${cell3.prisonId}"
                  }
                ]
              }
            ]
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }
}
