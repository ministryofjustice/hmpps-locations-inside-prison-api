package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.MovementCount
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonRollMovementInfo

class PrisonRollResourceIntTest : CommonDataTestBase() {

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

        webTestClient.get().uri("/prison/roll-count/NMI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
          {
            "prisonId": "NMI",
            "numUnlockRollToday": 1,
            "numCurrentPopulation": 2,
            "numArrivedToday": 2,
            "numInReception": 0,
            "numStillToArrive": 1,
            "numOutToday": 1,
            "numNoCellAllocated": 0,
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
            false,
          )
      }

      @Test
      fun `can obtain a role count for MDI sub-level`() {
        prisonerSearchMockServer.stubSearchByLocations(landingZ1.prisonId, landingZ1.cellLocations().map { it.getPathHierarchy() }, true)
        prisonApiMockServer.stubMovementsToday(landingZ1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))

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
                  "netVacancies": 2,
                  "outOfOrder": 0
                },
                "locations": [
                  {
                    "key": "MDI-Z-1",
                    "locationType": "LANDING",
                    "locationCode": "1",
                    "fullLocationPath": "Z-1",
                    "localName": "Landing 1",
                    "certified": true,
                    "rollCount": {
                      "bedsInUse": 2,
                      "currentlyInCell": 2,
                      "currentlyOut": 0,
                      "workingCapacity": 4,
                      "netVacancies": 2,
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
                          "currentlyInCell": 1,
                          "currentlyOut": 0,
                          "workingCapacity": 2,
                          "netVacancies": 1,
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
                  }
                ]
              }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `can obtain a role count for MDI`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))

        webTestClient.get().uri("/prison/roll-count/MDI")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 3,
              "numCurrentPopulation": 2,
              "numArrivedToday": 1,
              "numInReception": 0,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 0,
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
            false,
          )
      }

      @Test
      fun `can obtain a role count for MDI with cells`() {
        prisonerSearchMockServer.stubAllPrisonersInPrison(cell1.prisonId)
        prisonApiMockServer.stubMovementsToday(cell1.prisonId, PrisonRollMovementInfo(inOutMovementsToday = MovementCount(1, 2), enRouteToday = 2))

        webTestClient.get().uri("/prison/roll-count/MDI?include-cells=true")
          .headers(setAuthorisation(roles = listOf("ESTABLISHMENT_ROLL")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """ 
            {
              "prisonId": "MDI",
              "numUnlockRollToday": 3,
              "numCurrentPopulation": 2,
              "numArrivedToday": 1,
              "numInReception": 0,
              "numStillToArrive": 2,
              "numOutToday": 2,
              "numNoCellAllocated": 0,
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
                            "netVacancies": 1,
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
            false,
          )
      }
    }
  }
}
