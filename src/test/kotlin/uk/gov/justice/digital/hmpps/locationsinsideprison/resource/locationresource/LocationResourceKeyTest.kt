package uk.gov.justice.digital.hmpps.locationsinsideprison.resource.locationresource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

const val EXPECTED_USERNAME_PRISON = "A_TEST_USER"

@WithMockAuthUser(username = EXPECTED_USERNAME_PRISON)
class LocationResourceKeyTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: LocationRepository
  lateinit var cell1: Cell
  lateinit var cell2: Cell
  lateinit var inactiveCellB3001: Cell
  lateinit var archivedCell: Cell
  lateinit var landingZ1: ResidentialLocation
  lateinit var landingZ2: ResidentialLocation
  lateinit var landingB3: ResidentialLocation
  lateinit var landingN1: ResidentialLocation
  lateinit var wingZ: ResidentialLocation
  lateinit var wingB: ResidentialLocation
  lateinit var wingN: ResidentialLocation
  lateinit var visitRoom: NonResidentialLocation
  lateinit var adjRoom: NonResidentialLocation

  @BeforeEach
  fun setUp() {
    prisonerSearchMockServer.resetAll()
    repository.deleteAll()

    wingN = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A",
        locationType = LocationType.WING,
        localName = "WING A",
      ),
    )

    landingN1 = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A-1",
        locationType = LocationType.LANDING,
        localName = "LANDING A",
      ),
    )

    wingN.addChildLocation(landingN1)
    repository.save(wingN)

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
    landingB3 = repository.save(
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
    inactiveCellB3001 = repository.save(
      buildCell(
        pathHierarchy = "B-A-001",
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
        landingZ1
          .addChildLocation(cell1)
          .addChildLocation(cell2),
      )
      .addChildLocation(landingZ2)

    wingZ.updateComments(
      "A New Comment",
      uk.gov.justice.digital.hmpps.locationsinsideprison.resource.EXPECTED_USERNAME,
      clock,
    )

    wingB.addChildLocation(landingB3.addChildLocation(inactiveCellB3001))
    repository.save(wingZ)
    repository.save(wingB)
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
}
