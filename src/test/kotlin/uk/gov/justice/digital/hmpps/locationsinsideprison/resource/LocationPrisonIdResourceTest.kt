package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBodyList
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationPrisonIdResourceTest : CommonDataTestBase() {

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
                  "pathHierarchy": "Z-ADJUDICATION",
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
                  "key": "MDI-Z-ADJUDICATION"
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
                  "deactivatedDate": "2023-12-05T12:34:56",
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
                  "code": "01S",
                  "pathHierarchy": "Z-1-01S",
                  "locationType": "STORE",
                  "leafLevel": true,
                  "localName": "Store Room",
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1-01S"
                },
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "leafLevel": true,
                  "level": 2,
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

  @DisplayName("GET /locations/prison/{prisonId}/groups")
  @Nested
  inner class ViewLocationGroupsInPropertiesByPrisonTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/MDI/groups")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/MDI/groups")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/MDI/groups")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve residential groups for a prison included in the properties file`() {
        val result = webTestClient.get().uri("/locations/prison/MDI/groups")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBodyList(LocationGroupDto::class.java).hasSize(2)

        assertThat(result).isNotNull

        val firstGroup = result.returnResult().responseBody?.get(0)
        assertThat(firstGroup?.name).isEqualTo("All Wings")
        assertThat(firstGroup?.key).isEqualTo("All Wings")
        assertThat(firstGroup?.children).isEmpty()

        val secondGroup = result.returnResult().responseBody?.get(1)
        assertThat(secondGroup?.name).isEqualTo("Z-Wing")
        assertThat(secondGroup?.key).isEqualTo("Z-Wing")
        assertThat(secondGroup?.children).hasSize(2)

        val firstChildOfSecondGroup = secondGroup?.children?.get(0)
        assertThat(firstChildOfSecondGroup?.name ?: "Landing 1").isEqualTo("Landing 1")
        assertThat(firstChildOfSecondGroup?.key ?: "Landing 1").isEqualTo("Landing 1")
        assertThat(firstChildOfSecondGroup?.children).isEmpty()

        val secondChildOfSecondGroup = secondGroup?.children?.get(1)
        assertThat(secondChildOfSecondGroup?.name ?: "Landing 2").isEqualTo("Landing 2")
        assertThat(secondChildOfSecondGroup?.key ?: "Landing 2").isEqualTo("Landing 2")
        assertThat(secondChildOfSecondGroup?.children).isEmpty()
      }

      @Test
      fun `can retrieve cells for a group`() {
        webTestClient.get().uri("/locations/groups/MDI/Houseblock 1")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                       [ ]
                                              """,
            false,
          )
      }

      @Test
      fun `can retrieve residential groups for a prison NOT included in the properties file`() {
        webTestClient.get().uri("/locations/prison/NMI/groups")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
               [
                {
                  "name": "Wing A",
                  "key": "A",
                  "children": [
                    {
                      "name": "Landing A",
                      "key": "1",
                      "children": []
                    }
                  ]
                }
              ]
                        """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/group/{group}/location-prefix")
  @Nested
  inner class ViewLocationPrefixInPropertiesByPrisonAndGroupTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/MDI/group/Houseblock 1/location-prefix")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/MDI/group/Houseblock 1/location-prefix")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/MDI/group/Houseblock 1/location-prefix")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve location prefix for a prison included in the properties file`() {
        webTestClient.get().uri("/locations/prison/MDI/group/Z-Wing/location-prefix")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
                {
                "locationPrefix": "MDI-Z-"
                }
              """,
            false,
          )
      }
    }

    @Nested
    inner class FailurePath {
      @Test
      fun `resource not found`() {
        webTestClient.get().uri("/locations/prison/XYI/group/Houseblock 1/location-prefix")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().is4xxClientError
          .expectBody().json(
            """
                        {"status":404,
                        "userMessage":"Location prefix not found for XYI_Houseblock 1",
                        "developerMessage":"Location prefix not found for XYI_Houseblock 1",
                        "errorCode":111}
                        """,
            false,
          )
      }
    }
  }

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
                "accommodationTypes":[],
                "permanentlyInactive":true,
                "capacity":{"maxCapacity":0,"workingCapacity":0},
                "status":"ARCHIVED",
                "active":false,
                "deactivatedByParent":false,
                "deactivatedDate":"2023-12-05T12:34:56",
                "key":"MDI-Z-1-003",
                "permanentlyInactiveReason": "Demolished"
                }]
          """,
            false,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/inactive-cells")
  @Nested
  inner class ViewInactiveCellsTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of an inactive cells for establishment`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
[
                {
                  "id": "${inactiveCellB3001.id}",
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "B-A-001",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "oldWorkingCapacity": 2,
                  "certification": {
                    "certified": true,
                    "capacityOfCertifiedCell": 2
                  },
                  "status": "INACTIVE",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "2023-12-05T12:34:56",
                  "deactivatedReason": "DAMAGED",
                  "topLevelId": "${wingB.id}",
                  "level": 3,
                  "leafLevel": true,
                  "parentId": "${landingB3.id}",
                  "lastModifiedBy": "A_TEST_USER",
                  "lastModifiedDate": "2023-12-05T12:34:56",
                  "key": "MDI-B-A-001",
                  "isResidential": true
                }
              ]
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of an inactive cells for different wing`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells?parentLocationId=${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                [ ]
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of an inactive cells for wing`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells?parentLocationId=${wingB.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
[
                {
                  "id": "${inactiveCellB3001.id}",
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "B-A-001",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "oldWorkingCapacity": 2,
                  "certification": {
                    "certified": true,
                    "capacityOfCertifiedCell": 2
                  },
                  "status": "INACTIVE",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "2023-12-05T12:34:56",
                  "deactivatedReason": "DAMAGED",
                  "topLevelId": "${wingB.id}",
                  "level": 3,
                  "leafLevel": true,
                  "parentId": "${landingB3.id}",
                  "lastModifiedBy": "A_TEST_USER",
                  "lastModifiedDate": "2023-12-05T12:34:56",
                  "key": "MDI-B-A-001",
                  "isResidential": true
                }
              ]
          """,
            false,
          )
      }

      @Test
      fun `can retrieve details of an inactive cells for landing`() {
        webTestClient.get().uri("/locations/prison/${wingB.prisonId}/inactive-cells?parentLocationId=${landingB3.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              [
                {
                  "id": "${inactiveCellB3001.id}",
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "B-A-001",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "oldWorkingCapacity": 2,
                  "certification": {
                    "certified": true,
                    "capacityOfCertifiedCell": 2
                  },
                  "status": "INACTIVE",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "2023-12-05T12:34:56",
                  "deactivatedReason": "DAMAGED",
                  "topLevelId": "${wingB.id}",
                  "level": 3,
                  "leafLevel": true,
                  "parentId": "${landingB3.id}",
                  "lastModifiedBy": "A_TEST_USER",
                  "lastModifiedDate": "2023-12-05T12:34:56",
                  "key": "MDI-B-A-001",
                  "isResidential": true
                }
              ]
          """,
            false,
          )
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
}
