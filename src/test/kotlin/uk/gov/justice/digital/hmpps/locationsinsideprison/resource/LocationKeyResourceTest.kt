package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationKeyResourceTest : CommonDataTestBase() {

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
    inner class Validation {

      @Test
      fun `cannot get location keyId is not found`() {
        webTestClient.get().uri("/locations/key/XXX")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isNotFound
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
                      "leafLevel": false,
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
                          "code": "ADJUDICATION",
                          "pathHierarchy": "Z-ADJUDICATION",
                          "locationType": "ADJUDICATION_ROOM",
                          "usage": [
                            {
                              "usageType": "ADJUDICATION_HEARING",
                              "capacity": 15,
                              "sequence": 1
                            }
                          ],
                          "status": "ACTIVE",
                          "level": 2,
                          "leafLevel": true,
                          "key": "MDI-Z-ADJUDICATION",
                          "isResidential": false
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
                              "accommodationTypes":["CARE_AND_SEPARATION"],
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
                            },
                            {
                              "prisonId": "MDI",
                              "code": "01S",
                              "pathHierarchy": "Z-1-01S",
                              "locationType": "STORE",
                              "leafLevel": true,
                              "localName": "Store Room",
                              "active": true,
                              "isResidential": true,
                              "key": "MDI-Z-1-01S"
                          }
                          ],
                          
                          "active": true,
                          "accommodationTypes":["NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION"],
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
                          "leafLevel": false,
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
                      "accommodationTypes":["NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION"],
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
                            "key": "MDI-Z-1-003",
                            "leafLevel": true,
                            "level": 3
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "2",
                            "pathHierarchy": "Z-2",
                            "key": "MDI-Z-2",
                            "leafLevel": false,
                            "level": 2
                          }, 
                          {
                            "prisonId": "MDI",
                            "code": "VISIT",
                            "pathHierarchy": "Z-VISIT",
                            "key": "MDI-Z-VISIT",
                            "leafLevel": true,
                            "level": 2
                          }]
                         """,
            false,
          )
      }
    }
  }
}
