package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LatestOffenderMovement
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.MovementCount
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.OffenderMovement
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonRollMovementInfo
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonerOvernightMovement
import java.time.LocalDate

class PrisonRollResourceIntTest : CommonDataTestBase() {

  @Autowired
  private lateinit var prisonConfigurationRepository: PrisonConfigurationRepository

  @DisplayName("GET /prison/roll-count/{prisonId}")
  @Nested
  inner class PrisonRollCountTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prison/roll-count/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `Bad request when try an invalid prison Id`() {
        webTestClient.get().uri("/prison/roll-count/XXX")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      fun `Not found when prison does not exist`() {
        webTestClient.get().uri("/prison/roll-count/XXI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can obtain a role count for NMI`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1N.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1N.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(2, 1), enRouteToday = 1))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1N.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/NMI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
          {
            "prisonId": "NMI",
            "numUnlockRollToday": 6,
            "numCurrentPopulation": 7,
            "numArrivedToday": 2,
            "numInReception": 4,
            "numStillToArrive": 1,
            "numOutToday": 1,
            "numNoCellAllocated": 1,
            "totals": {
              "bedsInUse": 0,
              "currentlyInCell": 0,
              "currentlyOut": 0,
              "workingCapacity": 2,
              "netVacancies": 2,
              "outOfOrder": 0
            },
            "locations": [
              {
                "key": "NMI-A",
                "locationType": "WING",
                "locationCode": "A",
                "fullLocationPath": "A",
                "localName": "Wing A",
                "certified": true,
                "rollCount": {
                  "bedsInUse": 0,
                  "currentlyInCell": 0,
                  "currentlyOut": 0,
                  "workingCapacity": 2,
                  "netVacancies": 2,
                  "outOfOrder": 0
                },
                "subLocations": [
                  {
                    "key": "NMI-A-1",
                    "locationType": "LANDING",
                    "locationCode": "1",
                    "fullLocationPath": "A-1",
                    "localName": "Landing A",
                    "certified": true,
                    "rollCount": {
                      "bedsInUse": 0,
                      "currentlyInCell": 0,
                      "currentlyOut": 0,
                      "workingCapacity": 2,
                      "netVacancies": 2,
                      "outOfOrder": 0
                    }
                  }
                ]
              }
            ]
          }        
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI sub-level`() {
        prisonerSearchMockServer.stubSearchByLocations(landingZ1.prisonId, landingZ1.cellLocations().map { it.getPathHierarchy() }, true)
        prisonApiMockServer.stubMovementsToday(landingZ1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()

        webTestClient.get().uri("/prison/roll-count/${landingZ1.prisonId}/cells-only/${landingZ1.id!!}")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
              {
              "locationHierarchy": [
                {
                  "id": "${wingZ.id!!}",
                  "prisonId": "MDI",
                  "code": "Z",
                  "type": "WING",
                  "pathHierarchy": "Z",
                  "level": 1
                },
                {
                  "id": "${landingZ1.id!!}",
                  "prisonId": "MDI",
                  "code": "1",
                  "type": "LANDING",
                  "pathHierarchy": "Z-1",
                  "level": 2
                }
              ],
                "totals": {
                  "bedsInUse": 2,
                  "currentlyInCell": 2,
                  "currentlyOut": 0,
                  "workingCapacity": 4,
                  "netVacancies": 3,
                  "outOfOrder": 0,
                  "cellsOvercrowded": 0,
                  "totalOvercrowded": 0
                },
                "locations": [
                  {
                    "key": "MDI-Z-1",
                    "locationType": "LANDING",
                    "locationCode": "1",
                    "fullLocationPath": "Z-1",
                    "localName": "Landing 1",
                    "certified": true,
                    "overcrowded": false,
                    "overcrowdedBy": 0,
                    "rollCount": {
                      "bedsInUse": 2,
                      "currentlyInCell": 2,
                      "currentlyOut": 0,
                      "workingCapacity": 4,
                      "netVacancies": 3,
                      "outOfOrder": 0,
                      "cellsOvercrowded": 0,
                      "totalOvercrowded": 0
                    },
                    "subLocations": [
                      {
                        "key": "MDI-Z-1-001",
                        "locationType": "CELL",
                        "locationCode": "001",
                        "fullLocationPath": "Z-1-001",
                        "localName": "001",
                        "certified": true,
                        "overcrowded": false,
                        "overcrowdedBy": 0,
                        "rollCount": {
                          "bedsInUse": 1,
                          "currentlyInCell": 1,
                          "currentlyOut": 0,
                          "workingCapacity": 2,
                          "netVacancies": 1,
                          "outOfOrder": 0,
                          "cellsOvercrowded": 0,
                          "totalOvercrowded": 0
                        }
                      },
                      {
                        "key": "MDI-Z-1-002",
                        "locationType": "CELL",
                        "locationCode": "002",
                        "fullLocationPath": "Z-1-002",
                        "localName": "002",
                        "certified": true,
                        "overcrowded": false,
                        "overcrowdedBy": 0,
                        "rollCount": {
                          "bedsInUse": 1,
                          "currentlyInCell": 1,
                          "currentlyOut": 0,
                          "workingCapacity": 2,
                          "netVacancies": 2,
                          "outOfOrder": 0,
                          "cellsOvercrowded": 0,
                          "totalOvercrowded": 0
                        }
                      },
                      {
                        "key": "MDI-Z-1-01S",
                        "locationType": "STORE",
                        "locationCode": "01S",
                        "fullLocationPath": "Z-1-01S",
                        "localName": "Store Room",
                        "certified": false,
                        "rollCount": {
                          "bedsInUse": 0,
                          "currentlyInCell": 0,
                          "currentlyOut": 0,
                          "workingCapacity": 0,
                          "netVacancies": 0,
                          "outOfOrder": 0
                        }
                      }
                    ]
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `reports overcrowding when cells hold more prisoners than their CNA`() {
        // Both active cells on Z-1 have a CNA of 2; stub 3 prisoners in each so each is overcrowded by 1
        prisonerSearchMockServer.stubSearchByLocations(
          landingZ1.prisonId,
          landingZ1.cellLocations().map { it.getPathHierarchy() },
          returnResult = true,
          numberOfPrisonersInCell = 3,
        )
        prisonApiMockServer.stubMovementsToday(landingZ1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))

        webTestClient.get().uri("/prison/roll-count/${landingZ1.prisonId}/cells-only/${landingZ1.id!!}")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "totals": {
                  "cellsOvercrowded": 2,
                  "totalOvercrowded": 2
                },
                "locations": [
                  {
                    "key": "MDI-Z-1",
                    "locationType": "LANDING",
                    "overcrowded": true,
                    "overcrowdedBy": 2,
                    "rollCount": {
                      "cellsOvercrowded": 2,
                      "totalOvercrowded": 2
                    },
                    "subLocations": [
                      {
                        "key": "MDI-Z-1-001",
                        "locationType": "CELL",
                        "overcrowded": true,
                        "overcrowdedBy": 1,
                        "rollCount": {
                          "bedsInUse": 3,
                          "workingCapacity": 2,
                          "cellsOvercrowded": 1,
                          "totalOvercrowded": 1
                        }
                      },
                      {
                        "key": "MDI-Z-1-002",
                        "locationType": "CELL",
                        "overcrowded": true,
                        "overcrowdedBy": 1,
                        "rollCount": {
                          "bedsInUse": 3,
                          "workingCapacity": 2,
                          "cellsOvercrowded": 1,
                          "totalOvercrowded": 1
                        }
                      },
                      {
                        "key": "MDI-Z-1-01S",
                        "locationType": "STORE",
                        "overcrowded": false,
                        "overcrowdedBy": 0,
                        "rollCount": {
                          "cellsOvercrowded": 0,
                          "totalOvercrowded": 0
                        }
                      }
                    ]
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(prisonId = cell1.prisonId, date = LocalDate.now(clock), offenderMovements = emptyList())
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 3,
                "outOfOrder": 1
              },
              "locations": [
                {
                  "key": "MDI-B",
                  "locationType": "WING",
                  "locationCode": "B",
                  "fullLocationPath": "B",
                  "localName": "Wing B",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 0,
                    "currentlyInCell": 0,
                    "currentlyOut": 0,
                    "workingCapacity": 0,
                    "netVacancies": 0,
                    "outOfOrder": 1
                  },
                  "subLocations": [
                    {
                      "key": "MDI-B-A",
                      "locationType": "LANDING",
                      "locationCode": "A",
                      "fullLocationPath": "B-A",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 1
                      }
                    }
                  ]
                },
                {
                  "key": "MDI-Z",
                  "locationType": "WING",
                  "locationCode": "Z",
                  "fullLocationPath": "Z",
                  "localName": "Z",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 2,
                    "currentlyInCell": 1,
                    "currentlyOut": 1,
                    "workingCapacity": 4,
                    "netVacancies": 3,
                    "outOfOrder": 0
                  },
                  "subLocations": [
                    {
                      "key": "MDI-Z-1",
                      "locationType": "LANDING",
                      "locationCode": "1",
                      "fullLocationPath": "Z-1",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 2,
                        "currentlyInCell": 1,
                        "currentlyOut": 1,
                        "workingCapacity": 4,
                        "netVacancies": 3,
                        "outOfOrder": 0
                      }
                    },
                    {
                      "key": "MDI-Z-2",
                      "locationType": "LANDING",
                      "locationCode": "2",
                      "fullLocationPath": "Z-2",
                      "localName": "Landing 2",
                      "certified": false,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 0
                      }
                    }
                  ]
                }
              ]
            }      
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI with num overnights`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonerSearchMockServer.stubPrisonersForOvernightCount(
          prisonId = cell1.prisonId,
          prisoners = listOf(
            PrisonerOvernightMovement(prisonerNumber = "A1111AA", lastMovementTypeCode = "ADM", status = "ACTIVE OUT"),
            PrisonerOvernightMovement(prisonerNumber = "A2222AA", lastMovementTypeCode = "CRT", status = "ACTIVE OUT"),
            PrisonerOvernightMovement(prisonerNumber = "A3333AA", lastMovementTypeCode = "TAP", status = "ACTIVE IN"),
            PrisonerOvernightMovement(prisonerNumber = "A4444AA", lastMovementTypeCode = "TRN", status = "ACTIVE OUT"),
          ),
        )

        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements(
          latestMovements = listOf(
            LatestOffenderMovement(offenderNo = "A2222AA", directionCode = "OUT", movementDate = LocalDate.of(2023, 12, 4), movementTime = java.time.LocalTime.of(11, 59)),
          ),
          expectedOffenderNumbers = listOf("A2222AA"),
        )
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )
        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$.numOvernights").isEqualTo(1)
      }

      @Test
      fun `can obtain a role count for MDI showing seg`() {
        prisonConfigurationRepository.findById("MDI").ifPresent {
          it.includeSegregationInRollCount = true
          prisonConfigurationRepository.save(it)
        }

        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(prisonId = cell1.prisonId, date = LocalDate.now(clock), offenderMovements = emptyList())
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 2,
                "outOfOrder": 1
              },
              "locations": [
                {
                  "key": "MDI-B",
                  "locationType": "WING",
                  "locationCode": "B",
                  "fullLocationPath": "B",
                  "localName": "Wing B",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 0,
                    "currentlyInCell": 0,
                    "currentlyOut": 0,
                    "workingCapacity": 0,
                    "netVacancies": 0,
                    "outOfOrder": 1
                  },
                  "subLocations": [
                    {
                      "key": "MDI-B-A",
                      "locationType": "LANDING",
                      "locationCode": "A",
                      "fullLocationPath": "B-A",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 1
                      }
                    }
                  ]
                },
                {
                  "key": "MDI-Z",
                  "locationType": "WING",
                  "locationCode": "Z",
                  "fullLocationPath": "Z",
                  "localName": "Z",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 2,
                    "currentlyInCell": 1,
                    "currentlyOut": 1,
                    "workingCapacity": 4,
                    "netVacancies": 2,
                    "outOfOrder": 0
                  },
                  "subLocations": [
                    {
                      "key": "MDI-Z-1",
                      "locationType": "LANDING",
                      "locationCode": "1",
                      "fullLocationPath": "Z-1",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 2,
                        "currentlyInCell": 1,
                        "currentlyOut": 1,
                        "workingCapacity": 4,
                        "netVacancies": 2,
                        "outOfOrder": 0
                      }
                    },
                    {
                      "key": "MDI-Z-2",
                      "locationType": "LANDING",
                      "locationCode": "2",
                      "fullLocationPath": "Z-2",
                      "localName": "Landing 2",
                      "certified": false,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 0
                      }
                    }
                  ]
                }
              ]
            }      
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI with cells`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI?include-cells=true")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 3,
                "outOfOrder": 1
              },
              "locations": [
                {
                  "key": "MDI-B",
                  "locationType": "WING",
                  "locationCode": "B",
                  "fullLocationPath": "B",
                  "localName": "Wing B",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 0,
                    "currentlyInCell": 0,
                    "currentlyOut": 0,
                    "workingCapacity": 0,
                    "netVacancies": 0,
                    "outOfOrder": 1
                  },
                  "subLocations": [
                    {
                      "key": "MDI-B-A",
                      "locationType": "LANDING",
                      "locationCode": "A",
                      "fullLocationPath": "B-A",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 1
                      }
                    }
                  ]
                },
                {
                  "key": "MDI-Z",
                  "locationType": "WING",
                  "locationCode": "Z",
                  "fullLocationPath": "Z",
                  "localName": "Z",
                  "certified": true,
                  "rollCount": {
                    "bedsInUse": 2,
                    "currentlyInCell": 1,
                    "currentlyOut": 1,
                    "workingCapacity": 4,
                    "netVacancies": 3,
                    "outOfOrder": 0
                  },
                  "subLocations": [
                    {
                      "key": "MDI-Z-1",
                      "locationType": "LANDING",
                      "locationCode": "1",
                      "fullLocationPath": "Z-1",
                      "localName": "Landing 1",
                      "certified": true,
                      "rollCount": {
                        "bedsInUse": 2,
                        "currentlyInCell": 1,
                        "currentlyOut": 1,
                        "workingCapacity": 4,
                        "netVacancies": 3,
                        "outOfOrder": 0
                      },
                      "subLocations": [
                        {
                          "key": "MDI-Z-1-001",
                          "locationType": "CELL",
                          "locationCode": "001",
                          "fullLocationPath": "Z-1-001",
                          "localName": "001",
                          "certified": true,
                          "rollCount": {
                            "bedsInUse": 1,
                            "currentlyInCell": 1,
                            "currentlyOut": 0,
                            "workingCapacity": 2,
                            "netVacancies": 1,
                            "outOfOrder": 0
                          }
                        },
                        {
                          "key": "MDI-Z-1-002",
                          "locationType": "CELL",
                          "locationCode": "002",
                          "fullLocationPath": "Z-1-002",
                          "localName": "002",
                          "certified": true,
                          "rollCount": {
                            "bedsInUse": 1,
                            "currentlyInCell": 0,
                            "currentlyOut": 1,
                            "workingCapacity": 2,
                            "netVacancies": 2,
                            "outOfOrder": 0
                          }
                        },
                        {
                          "key": "MDI-Z-1-01S",
                          "locationType": "STORE",
                          "locationCode": "01S",
                          "fullLocationPath": "Z-1-01S",
                          "localName": "Store Room",
                          "certified": false,
                          "rollCount": {
                            "bedsInUse": 0,
                            "currentlyInCell": 0,
                            "currentlyOut": 0,
                            "workingCapacity": 0,
                            "netVacancies": 0,
                            "outOfOrder": 0
                          }
                        }
                      ]
                    },
                    {
                      "key": "MDI-Z-2",
                      "locationType": "LANDING",
                      "locationCode": "2",
                      "fullLocationPath": "Z-2",
                      "localName": "Landing 2",
                      "certified": false,
                      "rollCount": {
                        "bedsInUse": 0,
                        "currentlyInCell": 0,
                        "currentlyOut": 0,
                        "workingCapacity": 0,
                        "netVacancies": 0,
                        "outOfOrder": 0
                      }
                    }
                  ]
                }
              ]
            }        
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI with an offender moving to court and then released`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 3), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = "2"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "OUT", movementSequence = "1"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI?include-cells=false")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 3,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 3,
                "outOfOrder": 1
              }
            }        
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI with two offenders moving to court and then released`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 4), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = "2"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1006AA", movementType = "REL", movementSequence = "2"),
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI?include-cells=false")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 4,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 3,
                "outOfOrder": 1
              }
            }        
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can obtain a role count for MDI with an offender moving to court and then back and then released`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))
        prisonApiMockServer.stubLatestOffenderMovements()
        prisonApiMockServer.stubOffenderMovementsToday(
          prisonId = cell1.prisonId,
          date = LocalDate.now(clock),
          offenderMovements = listOf(
            OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
            OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = "3"), // moved back into prison then released later, so a non-sequential sequence
          ),
        )

        webTestClient.get().uri("/prison/roll-count/MDI?include-cells=false")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 8,
              "numCurrentPopulation": 7,
              "numArrivedToday": 1,
              "numInReception": 4,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 1,
              "totals": {
                "bedsInUse": 2,
                "currentlyInCell": 1,
                "currentlyOut": 1,
                "workingCapacity": 4,
                "netVacancies": 3,
                "outOfOrder": 1
              }
            }        
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }
}
