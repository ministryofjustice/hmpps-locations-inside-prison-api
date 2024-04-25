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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import java.time.Clock
import java.time.LocalDate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDTO
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Certification as CertificationDTO
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
  lateinit var landing1: ResidentialLocationJPA
  lateinit var landing2: ResidentialLocationJPA
  lateinit var landing3: ResidentialLocationJPA
  lateinit var wingZ: ResidentialLocationJPA
  lateinit var wingB: ResidentialLocationJPA
  lateinit var visitRoom: NonResidentialLocationJPA
  lateinit var adjRoom: NonResidentialLocationJPA

  @BeforeEach
  fun setUp() {
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

      ),
    )
    inactiveCell = repository.save(
      buildCell(
        pathHierarchy = "B-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        active = false,
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
                "WHEELCHAIR_ACCESSIBLE"
              ],
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "orderWithinParentLocation": 99,
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
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "capacityOfCertifiedCell": 0
                  },
                  "orderWithinParentLocation": 99,
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

  @DisplayName("GET /locations/residential/{prisonId}/below-parent")
  @Nested
  inner class ViewLocationsBelowParent {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/residential/${wingZ.prisonId}/below-parent")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/residential/${wingZ.prisonId}/below-parent")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/residential/${wingZ.prisonId}/below-parent")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve details of a locations on a wing`() {
        webTestClient.get().uri("/locations/residential/MDI/below-parent")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
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
                "WHEELCHAIR_ACCESSIBLE"
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
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of a locations on a landing`() {
        webTestClient.get().uri("/locations/residential/MDI/below-parent?parentLocationId=${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
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
                "WHEELCHAIR_ACCESSIBLE"
              ]
            },
             {
                "prisonId": "MDI",
                "code": "2",
                "pathHierarchy": "Z-2",
                "locationType": "LANDING",
                "residentialHousingType": "NORMAL_ACCOMMODATION",
                "inactiveCells": 0,
                "capacity": {
                  "maxCapacity": 0,
                  "workingCapacity": 0
                },
                "certification": {
                  "certified": false,
                  "capacityOfCertifiedCell": 0
                },
                "orderWithinParentLocation": 99,
                "active": true,
                "isResidential": true,
                "key": "MDI-Z-2"
            }
          ]
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of a locations on another landing by path`() {
        webTestClient.get().uri("/locations/residential/MDI/below-parent?parentPathHierarchy=${wingB.getPathHierarchy()}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            [
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
                "WHEELCHAIR_ACCESSIBLE"
              ]
            }
          ]
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
                          "orderWithinParentLocation": 99,
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
                              "orderWithinParentLocation": 99,
                              "active": true,
                              "residentialHousingType": "NORMAL_ACCOMMODATION",
                              "capacity": {
                                "maxCapacity": 2,
                                "workingCapacity": 2
                              },
                              "certification": {
                                "certified": true,
                                "capacityOfCertifiedCell": 2
                              },
                              "attributes": [
                                "DOUBLE_OCCUPANCY",
                                "CAT_B"
                              ],
                              "isResidential": true,
                              "key": "MDI-Z-1-001"
                            },
                            {
                              "prisonId": "MDI",
                              "code": "002",
                              "pathHierarchy": "Z-1-002",
                              "locationType": "CELL",
                              "orderWithinParentLocation": 99,
                              "active": true,
                              "residentialHousingType": "NORMAL_ACCOMMODATION",
                              "capacity": {
                                "maxCapacity": 2,
                                "workingCapacity": 2
                              },
                              "certification": {
                                "certified": true,
                                "capacityOfCertifiedCell": 2
                              },
                              "attributes": [
                                "DOUBLE_OCCUPANCY",
                                "CAT_A",
                                "SAFE_CELL"
                              ],
                              "isResidential": true,
                              "key": "MDI-Z-1-002"
                            }
                          ],
                          "orderWithinParentLocation": 99,
                          "active": true,
                          "residentialHousingType": "NORMAL_ACCOMMODATION",
                          "capacity": {
                            "maxCapacity": 4,
                            "workingCapacity": 4
                          },
                          "certification": {
                            "certified": true,
                            "capacityOfCertifiedCell": 4
                          },
                          "attributes": [
                            "DOUBLE_OCCUPANCY",
                            "CAT_B",
                            "CAT_A",
                            "SAFE_CELL"
                          ],
                          "isResidential": true,
                          "key": "MDI-Z-1"
                        },
                        {
                          "prisonId": "MDI",
                          "code": "2",
                          "pathHierarchy": "Z-2",
                          "locationType": "LANDING",
                          "orderWithinParentLocation": 99,
                          "active": true,
                          "residentialHousingType": "NORMAL_ACCOMMODATION",
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
                      "orderWithinParentLocation": 99,
                      "active": true,
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
                      "capacity": {
                        "maxCapacity": 4,
                        "workingCapacity": 4
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 4
                      },
                      "attributes": [
                        "DOUBLE_OCCUPANCY",
                        "CAT_B",
                        "CAT_A",
                        "SAFE_CELL"
                      ],
                      "isResidential": true,
                      "key": "MDI-Z"
                    }
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
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "isResidential": true,
                  "key": "MDI-Z"
                },
                {
                  "prisonId": "MDI",
                  "code": "B",
                  "pathHierarchy": "B",
                  "locationType": "WING",
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "isResidential": true,
                  "key": "MDI-B"
                },
                {
                  "prisonId": "MDI",
                  "code": "A",
                  "pathHierarchy": "B-A",
                  "locationType": "LANDING",
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "permanentlyInactive": false,
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-B-A"
                },
                {
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "B-A-001",
                  "locationType": "CELL",
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "orderWithinParentLocation": 99,
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
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "isResidential": true,
                  "key": "MDI-Z-1"
                },
                                { 
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
                },
                {
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "Z-1-001",
                  "locationType": "CELL",
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "isResidential": true,
                  "key": "MDI-Z-1-001"
                },
                {
                  "prisonId": "MDI",
                  "code": "002",
                  "pathHierarchy": "Z-1-002",
                  "locationType": "CELL",
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "isResidential": true,
                  "key": "MDI-Z-1-002"
                },
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "orderWithinParentLocation": 99,
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
        webTestClient.get().uri("/locations?size=10&sort=pathHierarchy,asc")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "totalPages": 1,
                "totalElements": 10,
                "first": true,
                "last": true,
                "size": 10,
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
                "numberOfElements": 10,
                "pageable": {
                  "offset": 0,
                  "sort": {
                    "empty": false,
                    "sorted": true,
                    "unsorted": false
                  },
                  "pageSize": 10,
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
      defaultAttributesOfCells = setOf(ResidentialAttributeValue.SINGLE_OCCUPANCY),
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
              "orderWithinParentLocation": 1,
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
          .bodyValue(createWingRequest.copy(wingCode = "X", wingDescription = "X Wing", numberOfLandings = 4, numberOfSpursPerLanding = 0, numberOfCellsPerSection = 2, defaultCellCapacity = 2, defaultAttributesOfCells = setOf(ResidentialAttributeValue.DOUBLE_OCCUPANCY)))
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
              "orderWithinParentLocation": 1,
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
      locationType = LocationType.CELL,
      localName = "A New Cell (004)",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 4,
      attributes = setOf(ResidentialAttributeValue.DOUBLE_OCCUPANCY, ResidentialAttributeValue.CAT_B),
      capacity = CapacityDTO(maxCapacity = 2, workingCapacity = 2),
      certification = CertificationDTO(certified = true, capacityOfCertifiedCell = 2),
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
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "active": true,
              "key": "MDI-Z-1-004",
              "comments": "This is a new cell",
              "localName": "A New Cell (004)",
              "orderWithinParentLocation": 4,
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 2
              },
              "attributes": [
                "DOUBLE_OCCUPANCY",
                "CAT_B"
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
      locationType = LocationType.ADJUDICATION_ROOM,
      localName = "Adjudication Room",
      comments = "This room is for adjudications",
      orderWithinParentLocation = 1,
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
              "comments": "${createNonResidentialLocationRequest.comments}",
              "localName": "${createNonResidentialLocationRequest.localName}",
              "orderWithinParentLocation": 1,
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

  @DisplayName("PATCH /locations/{id}")
  @Nested
  inner class PatchLocationTest {
    val changeCode = PatchLocationRequest(
      code = "3",
    )

    val changeCapacity = PatchLocationRequest(
      capacity = CapacityDTO(maxCapacity = 3, workingCapacity = 3),
    )
    val changeCertification = PatchLocationRequest(
      certification = CertificationDTO(certified = false, capacityOfCertifiedCell = 3),
    )

    val changeAttribute = PatchLocationRequest(
      attributes = setOf(
        ResidentialAttributeValue.SINGLE_OCCUPANCY,
        ResidentialAttributeValue.CAT_C,
      ),
    )

    val removeAttributes = PatchLocationRequest(
      attributes = emptySet(),
    )

    val changeUsage = PatchLocationRequest(
      code = "MEDICAL",
      locationType = LocationType.APPOINTMENTS,
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.APPOINTMENT, capacity = 20, sequence = 1),
      ),
    )

    val removeUsage = PatchLocationRequest(
      usage = emptySet(),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
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
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update to existing location`() {
        webTestClient.patch().uri("/locations/${adjRoom.id}")
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
        webTestClient.patch().uri("/locations/${landing1.id}")
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
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchLocationRequest(parentId = wingB.id)))
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
                "residentialHousingType": "NORMAL_ACCOMMODATION",
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
                  "orderWithinParentLocation": 99,
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
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
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
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
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
      fun `can update details of a locations capacity`() {
        webTestClient.patch().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCapacity))
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
                "residentialHousingType": "NORMAL_ACCOMMODATION",
                "capacity": {
                  "maxCapacity": 3,
                  "workingCapacity": 3
                },
                "certification": {
                  "certified": true,
                  "capacityOfCertifiedCell": 2
                },
                "attributes": [
                  "DOUBLE_OCCUPANCY",
                  "CAT_B"
                ],
                "isResidential": true,
                "key": "MDI-Z-1-001"
              }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
                "maxCapacity": 5,
                "workingCapacity": 5
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "childLocations": [
                {
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "key": "MDI-Z-VISIT"
                },
                {
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "key": "MDI-Z-1",
                  "capacity": {
                    "maxCapacity": 5,
                    "workingCapacity": 5
                  },
                  "certification": {
                    "capacityOfCertifiedCell": 4
                  },
                  "childLocations": [
                    {
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 3,
                        "workingCapacity": 3
                      },
                      "certification": {
                        "certified": true,
                        "capacityOfCertifiedCell": 2
                      },
                      "changeHistory": [
                        {
                          "attribute": "Max Capacity",
                          "oldValue": "2",
                          "newValue": "3"
                        },
                        {

                          "attribute": "Working Capacity",
                          "oldValue": "2",
                          "newValue": "3"
                        }
                      ]
                    },
                    {
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
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
                  }
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can update details of a locations certification`() {
        webTestClient.patch().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCertification))
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
                "residentialHousingType": "NORMAL_ACCOMMODATION",
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 2
                },
                "certification": {
                  "certified": false,
                  "capacityOfCertifiedCell": 3
                },
                "attributes": [
                  "DOUBLE_OCCUPANCY",
                  "CAT_B"
                ],
                "isResidential": true,
                "key": "MDI-Z-1-001"
              }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "capacityOfCertifiedCell": 5
              },
              "childLocations": [
                {
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "key": "MDI-Z-VISIT"
                },
                {
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "key": "MDI-Z-1",
                  "capacity": {
                    "maxCapacity": 4,
                    "workingCapacity": 4
                  },
                  "certification": {
                    "capacityOfCertifiedCell": 5
                  },
                  "childLocations": [
                    {
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "certification": {
                        "certified": false,
                        "capacityOfCertifiedCell": 3
                      },
                      "changeHistory": [
                        {
                          "attribute": "Certified",
                          "oldValue": "true",
                          "newValue": "false"
                        },
                        {
                          "attribute": "Baseline Certified Capacity",
                          "oldValue": "2",
                          "newValue": "3"
                        }
                      ]
                    },
                    {
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
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
                  }
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can delete details of a locations attributes`() {
        webTestClient.patch().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(removeAttributes))
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
              "active": true,
              "key": "MDI-Z-1-001",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "attributes": []
            }
          """,
            false,
          )

        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
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
              "active": true,
              "key": "MDI-Z-1-001",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "attributes": [],
              "changeHistory": [
                {
                  "attribute": "Attributes",
                  "oldValue": "DOUBLE_OCCUPANCY"
                },
                {
                  "attribute": "Attributes",
                  "oldValue": "CAT_B"
                }
              ]
            }
            """,
            false,
          )
      }

      @Test
      fun `can update details of a locations attributes`() {
        webTestClient.patch().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeAttribute))
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
              "active": true,
              "key": "MDI-Z-1-001",
              "residentialHousingType": "NORMAL_ACCOMMODATION",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "attributes": [
                "SINGLE_OCCUPANCY",
                "CAT_C"
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can update details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/${visitRoom.id}")
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
        webTestClient.patch().uri("/locations/${visitRoom.id}")
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

  @DisplayName("PUT /locations/{id}/deactivate")
  @Nested
  inner class DeactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"deactivationReason": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a location`() {
        val now = LocalDate.now(clock)
        val proposedReactivationDate = now.plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.CLOSURE)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
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
                "residentialHousingType": "NORMAL_ACCOMMODATION",
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 0
                },
                "certification": {
                  "certified": true,
                  "capacityOfCertifiedCell": 2
                },
                "attributes": [
                  "SAFE_CELL",
                  "DOUBLE_OCCUPANCY",
                  "CAT_A"
                ],
                "orderWithinParentLocation": 99,
                "active": false,
                "deactivatedDate": "$now",
                "deactivatedReason": "DAMAGED",
                "permanentlyInactive": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "topLevelId": "${wingZ.id}",
                "isResidential": true,
                "key": "MDI-Z"
              }
          """,
            false,
          )

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
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
              "permanentlyInactive": false,
              "proposedReactivationDate": "$proposedReactivationDate",
              "deactivatedDate": "$now",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "attributes": [
                "SAFE_CELL",
                "DOUBLE_OCCUPANCY",
                "CAT_A"
              ],
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
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "active": false,
                  "deactivatedByParent": true,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "attributes": [
                      "SAFE_CELL",
                      "DOUBLE_OCCUPANCY",
                      "CAT_A"
                    ],
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "$now",
                      "deactivatedReason": "CLOSURE",
                      "permanentlyInactive": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-001",
                      "attributes": [
                        "DOUBLE_OCCUPANCY",
                        "CAT_B"
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
                      "active": false,
                      "deactivatedByParent": true,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002",
                      "attributes": [
                        "DOUBLE_OCCUPANCY",
                        "CAT_A",
                        "SAFE_CELL"
                      ]
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "active": false,
                  "deactivatedByParent": true,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "isResidential": true,
                  "key": "MDI-Z-2",
                  "attributes": [ ]
                }
              ]
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
      fun `can reactivate a location`() {
        val proposedReactivationDate = LocalDate.now(clock).plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.CLOSURE)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
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
              }
            }
          """,
            false,
          )

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z",
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
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
                      "active": false,
                      "deactivatedByParent": false,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "${LocalDate.now(clock)}",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "residentialHousingType": "NORMAL_ACCOMMODATION",
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
                  "residentialHousingType": "NORMAL_ACCOMMODATION",
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
    }
  }
}
