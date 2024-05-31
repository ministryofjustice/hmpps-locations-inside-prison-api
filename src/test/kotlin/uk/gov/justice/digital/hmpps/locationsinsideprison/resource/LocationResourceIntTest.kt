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
import org.springframework.security.test.context.support.WithMockUser
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import java.time.Clock
import java.time.LocalDate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDTO
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation as NonResidentialLocationJPA
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation as ResidentialLocationJPA

const val EXPECTED_USERNAME = "A_TEST_USER"

@WithMockUser(username = EXPECTED_USERNAME)
class LocationResourceIntTest : SqsIntegrationTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
  }

  @Autowired
  lateinit var repository: LocationRepository
  lateinit var cell1: Cell
  lateinit var cell2: Cell
  lateinit var inactiveCell: Cell
  lateinit var archivedCell: Cell
  lateinit var landing1: ResidentialLocationJPA
  lateinit var landing2: ResidentialLocationJPA
  lateinit var landing3: ResidentialLocationJPA
  lateinit var wingZ: ResidentialLocationJPA
  lateinit var wingB: ResidentialLocationJPA
  lateinit var visitRoom: NonResidentialLocationJPA
  lateinit var adjRoom: NonResidentialLocationJPA

  @BeforeEach
  fun setUp() {
    prisonerSearchMockServer.resetAll()
    repository.deleteAll()

    wingZ = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z",
        locationType = LocationType.WING,
      ),
    )
    wingB = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B",
        locationType = LocationType.WING,
      ),
    )
    landing1 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-1",
        locationType = LocationType.LANDING,
      ),
    )
    landing2 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-2",
        locationType = LocationType.LANDING,
      ),
    )
    landing3 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B-A",
        locationType = LocationType.LANDING,
      ),
    )
    cell1 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
      ),
    )
    cell2 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-002",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        residentialAttributeValues = setOf(ResidentialAttributeValue.CAT_A, ResidentialAttributeValue.SAFE_CELL, ResidentialAttributeValue.DOUBLE_OCCUPANCY),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      ),
    )
    inactiveCell = repository.save(
      buildCell(
        pathHierarchy = "B-1-001",
        active = false,
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      ),
    )

    archivedCell = repository.save(
      buildCell(
        pathHierarchy = "Z-1-003",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        active = false,
        archived = true,
      ),
    )

    visitRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "VISIT",
        locationType = LocationType.VISITS,
        nonResidentialUsageType = NonResidentialUsageType.VISIT,
      ),
    )
    adjRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "ADJUDICATION",
        locationType = LocationType.ADJUDICATION_ROOM,
        nonResidentialUsageType = NonResidentialUsageType.ADJUDICATION_HEARING,
      ),
    )
    wingZ.addChildLocation(visitRoom)
      .addChildLocation(
        landing1
          .addChildLocation(cell1)
          .addChildLocation(cell2),
      )
      .addChildLocation(landing2)

    wingB.addChildLocation(landing3.addChildLocation(inactiveCell))
    repository.save(wingZ)
    repository.save(wingB)
  }

  @DisplayName("GET /locations")
  @Nested
  inner class ViewPagedLocationsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a page of locations`() {
        webTestClient.get().uri("/locations?size=11&sort=pathHierarchy,asc")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "totalPages": 1,
                "totalElements": 11,
                "first": true,
                "last": true,
                "size": 11,
                "content": [
                  {
                    "prisonId": "MDI",
                    "code": "ADJUDICATION",
                    "pathHierarchy": "ADJUDICATION",
                    "locationType": "ADJUDICATION_ROOM",
                    "isResidential": false,
                    "key": "MDI-ADJUDICATION"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "Z",
                    "pathHierarchy": "Z",
                    "locationType": "WING",
                    "key": "MDI-Z"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "B",
                    "pathHierarchy": "B",
                    "locationType": "WING",
                    "key": "MDI-B"
                  },
                  { 
                    "prisonId": "MDI",
                    "code": "VISIT",
                    "pathHierarchy": "Z-VISIT",
                    "locationType": "VISITS",
                    "key": "MDI-Z-VISIT"
                  }, 
                  {
                    "prisonId": "MDI",
                    "code": "1",
                    "pathHierarchy": "Z-1",
                    "locationType": "LANDING",
                    "key": "MDI-Z-1"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "A",
                    "pathHierarchy": "B-A",
                    "locationType": "LANDING",
                    "key": "MDI-B-A"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "2",
                    "pathHierarchy": "Z-2",
                    "locationType": "LANDING",
                    "key": "MDI-Z-2"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "001",
                    "pathHierarchy": "Z-1-001",
                    "locationType": "CELL",
                    "key": "MDI-Z-1-001"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "002",
                    "pathHierarchy": "Z-1-002",
                    "locationType": "CELL",
                    "key": "MDI-Z-1-002"
                  }, 
                  {
                    "prisonId": "MDI",
                    "code": "003",
                    "pathHierarchy": "Z-1-003",
                    "locationType": "CELL",
                    "key": "MDI-Z-1-003"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "001",
                    "pathHierarchy": "B-A-001",
                    "locationType": "CELL",
                    "key": "MDI-B-A-001"
                  }
                ],
                "number": 0,
                "sort": {
                  "empty": false,
                  "sorted": true,
                  "unsorted": false
                },
                "numberOfElements": 11,
                "pageable": {
                  "offset": 0,
                  "sort": {
                    "empty": false,
                    "sorted": true,
                    "unsorted": false
                  },
                  "pageSize": 11,
                  "pageNumber": 0,
                  "paged": true,
                  "unpaged": false
                },
                "empty": false
              }

              """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/{id}")
  @Nested
  inner class ViewLocationTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/${cell1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location`() {
        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ],
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  
                  "active": true,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "active": true,
                  "key": "MDI-Z-1",
                  "capacity": {
                    "maxCapacity": 4,
                    "workingCapacity": 4
                  },
                  "certification": {
                    "capacityOfCertifiedCell": 4
                  },
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "active": true,
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      }
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "active": true,
                      "key": "MDI-Z-1-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      }
                    }
                  ]
                },
                { 
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "capacityOfCertifiedCell": 0
                  },
                  
                  "active": true,
                  "childLocations": [],
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/residential-summary/{prisonId}")
  @Nested
  inner class ViewLocationsBelowParent {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/residential-summary/${wingZ.prisonId}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/residential-summary/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/residential-summary/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve details of a locations on a wing`() {
        webTestClient.get().uri("/locations/residential-summary/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {
              "prisonSummary": {
                "workingCapacity": 4,
                "signedOperationalCapacity": 0,
                "maxCapacity": 6
              },
              "subLocations":
            [
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "inactiveCells": 0,
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ]
            },
             {
              "prisonId": "MDI",
              "code": "B",
              "pathHierarchy": "B",
              "locationType": "WING",
              "active": true,
              "key": "MDI-B",
              "inactiveCells": 1,
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "certification": {
                "capacityOfCertifiedCell": 2
              }
            }
          ]
          }
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of a locations on a landing`() {
        webTestClient.get().uri("/locations/residential-summary/MDI?parentLocationId=${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
           {
              "parentLocation": {
                "prisonId": "MDI",
                "code": "Z",
                "pathHierarchy": "Z",
                "locationType": "WING",
                "permanentlyInactive": false,
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "specialistCellTypes": [
                  "ACCESSIBLE_CELL"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                
                "status": "ACTIVE",
                "active": true,
                "deactivatedByParent": false,
                "inactiveCells": 0,
                "key": "MDI-Z",
                "isResidential": true
              },
              "subLocations":               
              [
                {
                "prisonId": "MDI",
                "code": "1",
                "pathHierarchy": "Z-1",
                "locationType": "LANDING",
                "active": true,
                "key": "MDI-Z-1",
                "inactiveCells": 0,
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "capacityOfCertifiedCell": 4
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                "specialistCellTypes": [
                  "ACCESSIBLE_CELL"
                ]
              },
               {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "inactiveCells": 0,
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "capacityOfCertifiedCell": 0
                  },
                  
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
              }
            ]
          }
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of a locations on another landing by path`() {
        webTestClient.get().uri("/locations/residential-summary/MDI?parentPathHierarchy=${wingB.getPathHierarchy()}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
"""
              {
              "parentLocation": {
                "prisonId": "MDI",
                "code": "B",
                "pathHierarchy": "B",
                "locationType": "WING",
                "permanentlyInactive": false,
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 0
                },
                "certification": {
                  "certified": true
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "specialistCellTypes": [
                  "ACCESSIBLE_CELL"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                
                "status": "ACTIVE",
                "active": true,
                "deactivatedByParent": false,
                "inactiveCells": 1,
                "key": "MDI-B",
                "isResidential": true
              },
              "subLocations": [
                {
                  "prisonId": "MDI",
                  "code": "A",
                  "pathHierarchy": "B-A",
                  "locationType": "LANDING",
                  "active": true,
                  "key": "MDI-B-A",
                  "inactiveCells": 1,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "capacityOfCertifiedCell": 2
                  },
                  "accommodationTypes": [
                    "NORMAL_ACCOMMODATION"
                  ],
                  "usedFor": [
                    "STANDARD_ACCOMMODATION"
                  ],
                  "specialistCellTypes": [
                    "ACCESSIBLE_CELL"
                  ]
                }
              ]
            }
""",
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/key/{key}")
  @Nested
  inner class ViewLocationByKeyTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/key/${cell1.getKey()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/key/${cell1.getKey()}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/key/${cell1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location by key`() {
        webTestClient.get().uri("/locations/key/${wingZ.getKey()}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                    {
                      "prisonId": "MDI",
                      "code": "Z",
                      "pathHierarchy": "Z",
                      "locationType": "WING",
                      "childLocations": [
                        {
                          "prisonId": "MDI",
                          "code": "VISIT",
                          "pathHierarchy": "Z-VISIT",
                          "locationType": "VISITS",
                          
                          "active": true,
                          "usage": [
                            {
                              "usageType": "VISIT",
                              "capacity": 15,
                              "sequence": 1
                            }
                          ],
                          "isResidential": false,
                          "key": "MDI-Z-VISIT"
                        },
                        {
                          "prisonId": "MDI",
                          "code": "1",
                          "pathHierarchy": "Z-1",
                          "locationType": "LANDING",
                          "childLocations": [
                            {
                              "prisonId": "MDI",
                              "code": "001",
                              "pathHierarchy": "Z-1-001",
                              "locationType": "CELL",
                              
                              "active": true,
                              "accommodationTypes":["NORMAL_ACCOMMODATION"],
                              "capacity": {
                                "maxCapacity": 2,
                                "workingCapacity": 2
                              },
                              "certification": {
                                "certified": true,
                                "capacityOfCertifiedCell": 2
                              },
                              "isResidential": true,
                              "key": "MDI-Z-1-001"
                            },
                            {
                              "prisonId": "MDI",
                              "code": "002",
                              "pathHierarchy": "Z-1-002",
                              "locationType": "CELL",
                              
                              "active": true,
                              "accommodationTypes":["NORMAL_ACCOMMODATION"],
                              "capacity": {
                                "maxCapacity": 2,
                                "workingCapacity": 2
                              },
                              "certification": {
                                "certified": true,
                                "capacityOfCertifiedCell": 2
                              },
                              "isResidential": true,
                              "key": "MDI-Z-1-002"
                            }
                          ],
                          
                          "active": true,
                          "accommodationTypes":["NORMAL_ACCOMMODATION"],
                          "capacity": {
                            "maxCapacity": 4,
                            "workingCapacity": 4
                          },
                          "certification": {
                            "certified": true,
                            "capacityOfCertifiedCell": 4
                          },
                          "isResidential": true,
                          "key": "MDI-Z-1"
                        },
                        {
                          "prisonId": "MDI",
                          "code": "2",
                          "pathHierarchy": "Z-2",
                          "locationType": "LANDING",
                          "active": true,
                          "accommodationTypes":[],
                          "capacity": {
                            "maxCapacity": 0,
                            "workingCapacity": 0
                          },
                          "certification": {
                            "certified": false,
                            "capacityOfCertifiedCell": 0
                          },
                          "isResidential": true,
                          "key": "MDI-Z-2"
                        }
                      ],
                      
                      "active": true,
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "capacity": {
                        "maxCapacity": 4,
                        "workingCapacity": 4
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 4
                      },
                      "isResidential": true,
                      "key": "MDI-Z"
                    }
                        """,
            false,
          )
      }
    }
  }

  @DisplayName("POST /locations/keys")
  @Nested
  inner class ViewLocationByKeysTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/keys")
          .header("Content-Type", "application/json")
          .bodyValue(listOf("Z"))
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/keys")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(listOf("Z"))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/keys")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(listOf("Z"))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve locations from a list of keys`() {
        webTestClient.post().uri("/locations/keys")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .bodyValue(listOf("MDI-B-A", "MDI-B-A-001", "MDI-Z", "MDI-Z", "MDI-Z-2", "MDI-Z-VISIT", "MDI-Z-1-003", "XYZ-1-2-3"))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                          [{
                            "prisonId": "MDI",
                            "code": "A",
                            "pathHierarchy": "B-A",
                            "key": "MDI-B-A"
                          }, 
                          {
                          
                            "prisonId": "MDI",
                            "code": "001",
                            "pathHierarchy": "B-A-001",
                            "key": "MDI-B-A-001"
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "Z",
                            "pathHierarchy": "Z",
                            "key": "MDI-Z"
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "003",
                            "pathHierarchy": "Z-1-003",
                            "key": "MDI-Z-1-003"
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "2",
                            "pathHierarchy": "Z-2",
                            "key": "MDI-Z-2"
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "VISIT",
                            "pathHierarchy": "Z-VISIT",
                            "key": "MDI-Z-VISIT"
                          }]
                         """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}")
  @Nested
  inner class ViewLocationByPrisonTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${cell1.prisonId}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${cell1.prisonId}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${cell1.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             [
               {
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "usage": [
                    {
                      "usageType": "ADJUDICATION_HEARING",
                      "capacity": 15,
                      "sequence": 1
                    }
                  ],
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-ADJUDICATION"
                },
                {
                  "prisonId": "MDI",
                  "code": "Z",
                  "pathHierarchy": "Z",
                  "locationType": "WING",
                  "active": true,
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "isResidential": true,
                  "key": "MDI-Z"
                },
                {
                  "prisonId": "MDI",
                  "code": "B",
                  "pathHierarchy": "B",
                  "locationType": "WING",
                  "active": true,
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "isResidential": true,
                  "key": "MDI-B"
                },
                {
                  "prisonId": "MDI",
                  "code": "A",
                  "pathHierarchy": "B-A",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "permanentlyInactive": false,
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-B-A"
                },
                {
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "B-A-001",
                  "locationType": "CELL",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "2023-12-05",
                  "deactivatedReason": "DAMAGED",
                  "isResidential": true,
                  "key": "MDI-B-A-001"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "active": true,
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "isResidential": true,
                  "key": "MDI-Z-1"
                },
                { 
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
                },
                {
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "Z-1-001",
                  "locationType": "CELL",
                  
                  "active": true,
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "isResidential": true,
                  "key": "MDI-Z-1-001"
                },
                {
                  "prisonId": "MDI",
                  "code": "002",
                  "pathHierarchy": "Z-1-002",
                  "locationType": "CELL",
                  
                  "active": true,
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "isResidential": true,
                  "key": "MDI-Z-1-002"
                },
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                }
              ]
          """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/archived")
  @Nested
  inner class ViewArchivedLocationByPrisonTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${archivedCell.prisonId}/archived")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${archivedCell.prisonId}/archived")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${archivedCell.prisonId}/archived")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of an archived locations`() {
        webTestClient.get().uri("/locations/prison/${archivedCell.prisonId}/archived")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             [{"prisonId":"MDI",
                "code":"003",
                "pathHierarchy":"Z-1-003",
                "locationType":"CELL",
                "accommodationTypes":["NORMAL_ACCOMMODATION"],
                "permanentlyInactive":true,
                "capacity":{"maxCapacity":0,"workingCapacity":0},
                "status":"INACTIVE",
                "active":false,
                "deactivatedByParent":false,
                "deactivatedDate":"2023-12-05",
                "key":"MDI-Z-1-003",
                "permanentlyInactiveReason": "Demolished"
                }]
          """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/location-type/{locationTYpe}")
  @Nested
  inner class ViewLocationsByLocationTypeTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/location-type/${cell1.locationType}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/location-type/${cell1.locationType}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/location-type/${cell1.locationType}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve locations by their type`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/location-type/${cell1.locationType}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             [{
              "prisonId": "MDI",
              "code": "001",
              "pathHierarchy": "Z-1-001",
              "locationType": "CELL",
              "active": true,
              "key": "MDI-Z-1-001"
            }, {
              "prisonId": "MDI",
              "code": "002",
              "pathHierarchy": "Z-1-002",
              "locationType": "CELL",
              "active": true,
              "key": "MDI-Z-1-002"
            }]
                      """,
            false,
          )
      }
    }
  }

  @DisplayName("POST /locations/create-wing")
  @Nested
  inner class CreateWingTest {
    var createWingRequest = CreateWingRequest(
      prisonId = "MDI",
      wingCode = "Y",
      wingDescription = "Y Wing",
      numberOfLandings = 3,
      numberOfSpursPerLanding = 2,
      numberOfCellsPerSection = 2,
      defaultCellCapacity = 1,
    )

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations/create-wing")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri("/locations/create-wing")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createWingRequest.copy(wingCode = "Z")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create an entire wing with 3 landings, 2 spurs and 2 cells per spur`() {
        webTestClient.post().uri("/locations/create-wing")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(createWingRequest)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "Y",
              "pathHierarchy": "Y",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Y",
              "localName": "Y Wing",
              
              "capacity": {
                "maxCapacity": 12,
                "workingCapacity": 12
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 12
              }
            }
          """,
            false,
          )

        getDomainEvents(12).let {
          assertThat(it).hasSize(12)
        }
      }

      @Test
      fun `can create an entire wing with 4 landings, 2 cells per landing`() {
        webTestClient.post().uri("/locations/create-wing")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(createWingRequest.copy(wingCode = "X", wingDescription = "X Wing", numberOfLandings = 4, numberOfSpursPerLanding = 0, numberOfCellsPerSection = 2, defaultCellCapacity = 2))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "X",
              "pathHierarchy": "X",
              "locationType": "WING",
              "active": true,
              "key": "MDI-X",
              "localName": "X Wing",
              
              "capacity": {
                "maxCapacity": 16,
                "workingCapacity": 16
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 16
              }
            }
          """,
            false,
          )

        getDomainEvents(8).let {
          assertThat(it).hasSize(8)
        }
      }
    }
  }

  @DisplayName("POST /locations/residential")
  @Nested
  inner class CreateResidentialLocationTest {
    var createResidentialLocationRequest = CreateResidentialLocationRequest(
      prisonId = "MDI",
      code = "004",
      locationType = ResidentialLocationType.CELL,
      localName = "A New Cell (004)",
      accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
      capacity = CapacityDTO(maxCapacity = 2, workingCapacity = 2),
      certified = true,
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/residential")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest.copy(code = "001", parentId = landing1.id)))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `invalid prison ID is rejected`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest.copy(prisonId = "FFO")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest.copy(parentId = landing1.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "004",
              "pathHierarchy": "Z-1-004",
              "locationType": "CELL",
              "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
              "active": true,
              "key": "MDI-Z-1-004",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 0
              },
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ]
            }
          """,
            false,
          )

        getDomainEvents(3).let {
          assertThat(it).hasSize(3)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "MDI-Z-1-004",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("POST /locations/non-residential")
  @Nested
  inner class CreateNonResidentialLocationTest {
    var createNonResidentialLocationRequest = CreateNonResidentialLocationRequest(
      prisonId = "MDI",
      code = "ADJ",
      locationType = NonResidentialLocationType.ADJUDICATION_ROOM,
      localName = "Adjudication Room",
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.ADJUDICATION_HEARING, capacity = 15, sequence = 1),
      ),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/non-residential")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest.copy(code = "VISIT", parentId = wingZ.id)))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest.copy(parentId = wingZ.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "ADJ",
              "pathHierarchy": "Z-ADJ",
              "locationType": "${createNonResidentialLocationRequest.locationType}",
              "active": true,
              "key": "MDI-Z-ADJ",
              "localName": "${createNonResidentialLocationRequest.localName}",
              "usage": [
                {
                  "usageType": "ADJUDICATION_HEARING",
                  "capacity": 15,
                  "sequence": 1
                }
              ]
            }
          """,
            false,
          )

        getDomainEvents(2).let {
          assertThat(it).hasSize(2)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "MDI-Z-ADJ",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/usage-type/{usageType}")
  @Nested
  inner class ViewNonResidentialLocationsByUsageTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential-usage-type/VISIT")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential-usage-type/VISIT")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential-usage-type/VISIT")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve locations from usage type`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential-usage-type/VISIT")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                          [{
                            "prisonId": "MDI",
                            "code": "VISIT",
                            "pathHierarchy": "Z-VISIT",
                            "locationType": "VISITS",
                            "usage": [{
                              "usageType": "VISIT"
                            }],
                            "key": "MDI-Z-VISIT"
                          }]
                         """,
            false,
          )
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return client error for invalid usage type`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential-usage-type/UNKNOWN")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }
  }

  @DisplayName("PATCH /locations/residential/{id}")
  @Nested
  inner class PatchLocationTest {
    val changeCode = PatchResidentialLocationRequest(
      code = "3",
    )

    val changeUsage = PatchNonResidentialLocationRequest(
      code = "MEDICAL",
      locationType = NonResidentialLocationType.APPOINTMENTS,
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.APPOINTMENT, capacity = 20, sequence = 1),
      ),
    )

    val removeUsage = PatchNonResidentialLocationRequest(
      usage = emptySet(),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update to existing location`() {
        webTestClient.patch().uri("/locations/non-residential/${adjRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(changeCode.copy(code = "VISIT", parentId = wingZ.id))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update details of a locations code`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "3",
              "pathHierarchy": "Z-3",
              "locationType": "LANDING",
              "active": true,
              "key": "MDI-Z-3",
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "Z-3-001",
                  "locationType": "CELL",
                  "active": true,
                  "key": "MDI-Z-3-001"
                },
                {
                  "prisonId": "MDI",
                  "code": "002",
                  "pathHierarchy": "Z-3-002",
                  "locationType": "CELL",
                  "active": true,
                  "key": "MDI-Z-3-002"
                }
              ]
            }
          """,
            false,
          )

        getDomainEvents(3).let {
          assertThat(it).hasSize(3)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-3",
            "location.inside.prison.amended" to "MDI-Z-3-001",
            "location.inside.prison.amended" to "MDI-Z-3-002",
          )
        }
      }

      @Test
      fun `can update parent of a location`() {
        webTestClient.patch().uri("/locations/residential/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(parentId = wingB.id)))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
               {
                "prisonId": "MDI",
                "code": "1",
                "pathHierarchy": "B-1",
                "locationType": "LANDING",
                "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true,
                  "capacityOfCertifiedCell": 4
                },
                "isResidential": true,
                "key": "MDI-B-1"
              }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "pathHierarchy": "Z",
              "locationType": "WING",
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "certification": {
                "certified": false,
                "capacityOfCertifiedCell": 0
              },
              "childLocations": [
                 {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "usage": [
                    {
                      "usageType": "VISIT",
                      "capacity": 15,
                      "sequence": 1
                    }
                  ],
                  
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "key": "MDI-Z-2",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "capacityOfCertifiedCell": 0
                  },
                  "childLocations": []
                }
              ]
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${wingB.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "pathHierarchy": "B",
              "locationType": "WING",
              "key": "MDI-B",
              "capacity": {
                "maxCapacity": 6,
                "workingCapacity": 4
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 6
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "A",
                  "pathHierarchy": "B-A",
                  "locationType": "LANDING",
                  "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": true,
                    "capacityOfCertifiedCell": 2
                  },
                  "active": true,
                  "deactivatedByParent": false,
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "B-A-001",
                      "locationType": "CELL",
                      "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
                      "permanentlyInactive": false,
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      },
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "2023-12-05",
                      "deactivatedReason": "DAMAGED",
                      "childLocations": [],
                      "isResidential": true,
                      "key": "MDI-B-A-001"
                    }
                  ],
                  "isResidential": true,
                  "key": "MDI-B-A"
                },              
                {
                  "code": "1",
                  "pathHierarchy": "B-1",
                  "locationType": "LANDING",
                  "key": "MDI-B-1",
                  "capacity": {
                    "maxCapacity": 4,
                    "workingCapacity": 4
                  },
                  "certification": {
                    "certified": true,
                    "capacityOfCertifiedCell": 4
                  },
                  "childLocations": [
                    {
                      "pathHierarchy": "B-1-001",
                      "locationType": "CELL",
                      "key": "MDI-B-1-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      }
                    },
                    {
                      "pathHierarchy": "B-1-002",
                      "locationType": "CELL",
                      "key": "MDI-B-1-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      }
                    }
                  ]
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can update details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "MEDICAL",
              "pathHierarchy": "Z-MEDICAL",
              "locationType": "APPOINTMENTS",
              "active": true,
              "key": "MDI-Z-MEDICAL",
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 20,
                  "sequence": 1
                }
              ]
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${visitRoom.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
               {
                 "key": "MDI-Z-MEDICAL",
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 20,
                     "sequence": 1
                   }
                 ],
                 "changeHistory": [
                    {
                      "attribute": "Code",
                      "oldValue": "VISIT",
                      "newValue": "MEDICAL"
                    },
                    {
                      "attribute": "Location Type",
                      "oldValue": "Visits",
                      "newValue": "Appointments"
                    },
                    {
                      "attribute": "Usage",
                      "oldValue": "VISIT"
                    },
                    {
                      "attribute": "Usage",
                      "newValue": "APPOINTMENT"
                    },
                    {
                      "attribute": "Non Residential Capacity",
                      "newValue": "20"
                    }
                  ]
               }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `can remove details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(removeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": []
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/key/MDI-Z-VISIT?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": [],
              "changeHistory": [
                {
                  "attribute": "Usage",
                  "oldValue": "VISIT"
                }
              ]
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/capacity")
  @Nested
  inner class CapacityChangeTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO()))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = -1, maxCapacity = 999)))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot reduce max capacity of a cell below prisoners in cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = 1, maxCapacity = 1)))
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot have a max cap below a working cap`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = 3, maxCapacity = 2)))
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot have a max cap of 0`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = 0, maxCapacity = 0)))
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot have a working cap = 0 when accommodation type = normal accommodation and not a specialist cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = 0, maxCapacity = 2)))
          .exchange()
          .expectStatus().isEqualTo(400)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can change the capacity of a cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDTO(workingCapacity = 1, maxCapacity = 2)))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "${cell1.id}",
                "prisonId": "MDI",
                "code": "001",
                "pathHierarchy": "Z-1-001",
                "locationType": "CELL",
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 1
                },
                "certification": {
                  "certified": true
                },
                "active": true,
                "isResidential": true,
                "key": "MDI-Z-1-001"
              }
          """,
            false,
          )

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/{id}/deactivate")
  @Nested
  inner class TemporarilyDeactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"deactivationReason": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot deactivate a location when prisoner is inside the cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
          .exchange()
          .expectStatus().isEqualTo(409)
      }

      @Test
      fun `cannot deactivate a wing when prisoners are in cells below`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
          .exchange()
          .expectStatus().isEqualTo(409)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDate.now(clock)
        val proposedReactivationDate = now.plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Cell destroyed")))
          .exchange()
          .expectStatus().isOk

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "${wingZ.id}",
                "prisonId": "MDI",
                "code": "Z",
                "pathHierarchy": "Z",
                "locationType": "WING",
                "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 0
                },
                "certification": {
                  "certified": true,
                  "capacityOfCertifiedCell": 2
                },
                "active": false,
                "deactivatedDate": "$now",
                "deactivatedReason": "DAMAGED",
                "permanentlyInactive": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "topLevelId": "${wingZ.id}",
                "isResidential": true,
                "key": "MDI-Z",
                "deactivatedBy": "LOCATIONS_INSIDE_PRISON_API"
              }
          """,
            false,
          )

        getDomainEvents(8).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": false,
              "deactivatedByParent": false,
              "key": "MDI-Z",
              "deactivatedReason": "DAMAGED",
              "accommodationTypes":["NORMAL_ACCOMMODATION"],
              "permanentlyInactive": false,
              "proposedReactivationDate": "$proposedReactivationDate",
              "deactivatedDate": "$now",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 2
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": false,
                  "deactivatedByParent": true,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": false,
                  "deactivatedByParent": false,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": false,
                      "deactivatedByParent": false,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": false,
                  "deactivatedByParent": false,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can update a deactivated location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDate.now(clock)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        val proposedReactivationDate = now.plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-001",
          )
        }

        webTestClient.get().uri("/locations/${cell1.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
                "prisonId": "MDI",
                "code": "001",
                "pathHierarchy": "Z-1-001",
                "locationType": "CELL",
                "accommodationTypes":["NORMAL_ACCOMMODATION"],
                "active": false,
                "deactivatedByParent": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "deactivatedDate": "$now",
                "deactivatedReason": "MOTHBALLED",
                "planetFmReference": "334423",
                "isResidential": true,
                "key": "MDI-Z-1-001"
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/reactivate")
  @Nested
  inner class ReactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can cascade reactivated locations`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/reactivate?cascade-reactivation=true")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(12).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1-002",
            "location.inside.prison.reactivated" to "MDI-Z-VISIT",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can reactivate a location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val proposedReactivationDate = LocalDate.now(clock).plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy(), cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(12).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 2
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": false,
                      "deactivatedReason": "MOTHBALLED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": false,
                  "deactivatedReason": "MOTHBALLED",
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }
    }
  }
}
