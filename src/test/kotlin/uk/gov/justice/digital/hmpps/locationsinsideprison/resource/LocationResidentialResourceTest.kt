package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.*

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationResidentialResourceTest : CommonDataTestBase() {

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
      fun `can retrieve details of a locations at establishment level`() {
        prisonRegisterMockServer.stubLookupPrison("MDI")

        webTestClient.get().uri("/locations/residential-summary/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {
              "topLevelLocationType": "Wings",
              "prisonSummary": {
                "workingCapacity": 4,
                "signedOperationalCapacity": 200,
                "maxCapacity": 6,
                "numberOfCellLocations": 4
              },
              "subLocations":
            [
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "level": 1,
              "leafLevel": false,
              "active": true,
              "key": "MDI-Z",
              "inactiveCells": 0,
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "certifiedNormalAccommodation": 4
              },
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION",
                "CARE_AND_SEPARATION"
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
              "level": 1,
              "key": "MDI-B",
              "inactiveCells": 1,
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "certification": {
                "certifiedNormalAccommodation": 2
              }
            }
          ]
          }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve details of a locations on a wing`() {
        val now = LocalDateTime.now(clock)
        webTestClient.get().uri("/locations/residential-summary/MDI?parentLocationId=${wingZ.id}&latestHistory=true")
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
                "level": 1,
                "permanentlyInactive": false,
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION",
                  "CARE_AND_SEPARATION"
                ],
                "specialistCellTypes": [
                  "ACCESSIBLE_CELL"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                "lastModifiedBy": "$EXPECTED_USERNAME",
                "lastModifiedDate": "$now",
                "status": "ACTIVE",
                "active": true,
                "deactivatedByParent": false,
                "inactiveCells": 0,
                "key": "MDI-Z",
                "isResidential": true,
                "numberOfCellLocations": 3
              },
              "topLevelLocationType": "Wings",
               "locationHierarchy": [
                  {
                    "prisonId": "MDI",
                    "code": "Z",
                    "pathHierarchy": "Z",
                    "level": 1
                  }
                ],
              "subLocations":               
              [
                {
                "prisonId": "MDI",
                "code": "1",
                "pathHierarchy": "Z-1",
                "locationType": "LANDING",
                "level": 2,
                "leafLevel": false,
                "active": true,
                "key": "MDI-Z-1",
                "inactiveCells": 0,
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certifiedNormalAccommodation": 4
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION",
                  "CARE_AND_SEPARATION"
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
                  "level": 2,
                  "accommodationTypes":[],
                  "inactiveCells": 0,
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "certifiedNormalAccommodation": 0
                  },

                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
              }
            ]
          }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve details of a locations on a landing`() {
        webTestClient.get().uri("/locations/residential-summary/MDI?parentLocationId=${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
           {
              "topLevelLocationType": "Wings",
              "parentLocation": {
                "prisonId": "MDI",
                "code": "1",
                "pathHierarchy": "Z-1",
                "locationType": "LANDING",
                "level": 2,
                "permanentlyInactive": false,
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true
                },
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION",
                  "CARE_AND_SEPARATION"
                ],
                "specialistCellTypes": [
                  "ACCESSIBLE_CELL"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                "numberOfCellLocations": 3,
                "status": "ACTIVE",
                "active": true,
                "inactiveCells": 0,
                "key": "MDI-Z-1"
              },
               "locationHierarchy": [
                  {
                    "prisonId": "MDI",
                    "code": "Z",
                    "type": "WING",
                    "pathHierarchy": "Z",
                    "level": 1
                  },
                  {
                    "prisonId": "MDI",
                    "code": "1",
                    "type": "LANDING",
                    "pathHierarchy": "Z-1",
                    "level": 2
                 }
                ],
              "subLocations":               
              [
                 {
                    "prisonId": "MDI",
                    "code": "001",
                    "pathHierarchy": "Z-1-001",
                    "locationType": "CELL",
                    "leafLevel": true,
                    "capacity": {
                      "maxCapacity": 2,
                      "workingCapacity": 2
                    },
                    "certification": {
                      "certified": true,
                      "certifiedNormalAccommodation": 2
                    },
                    "accommodationTypes": [
                      "NORMAL_ACCOMMODATION"
                    ],
                    "usedFor": [
                      "STANDARD_ACCOMMODATION"
                    ],
                    "status": "ACTIVE",
                    "active": true,
                    "level": 3,
                    "key": "MDI-Z-1-001"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "002",
                    "pathHierarchy": "Z-1-002",
                    "locationType": "CELL",
                    "leafLevel": true,
                    "capacity": {
                      "maxCapacity": 2,
                      "workingCapacity": 2
                    },
                    "certification": {
                      "certified": true,
                      "certifiedNormalAccommodation": 2
                    },
                    "accommodationTypes": [
                      "CARE_AND_SEPARATION"
                    ],
                    "specialistCellTypes": [
                      "ACCESSIBLE_CELL"
                    ],
                    "usedFor": [
                      "STANDARD_ACCOMMODATION"
                    ],
                    "status": "ACTIVE",
                    "active": true,
                    "level": 3,
                    "inactiveCells": 0,
                    "key": "MDI-Z-1-002"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "01S",
                    "pathHierarchy": "Z-1-01S",
                    "locationType": "STORE",
                    "localName": "Store Room"
                }
            ]
          }
          """,
            JsonCompareMode.LENIENT,
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
                "numberOfCellLocations": 1,
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
                    "certifiedNormalAccommodation": 2
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
            JsonCompareMode.LENIENT,
          )
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
      capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
      certified = true,
      certifiedNormalAccommodation = 2,
      inCellSanitation = true,
    )

    var createWing = CreateResidentialLocationRequest(
      prisonId = "LEI",
      code = "B",
      locationType = ResidentialLocationType.WING,
      accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
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
          .bodyValue(jsonString(createResidentialLocationRequest.copy(code = "001", parentId = landingZ1.id)))
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
          .bodyValue(jsonString(createResidentialLocationRequest.copy(parentId = landingZ1.id)))
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
                "certifiedNormalAccommodation": 2
              },
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "inCellSanitation": true
            }
          """,
            JsonCompareMode.LENIENT,
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

      @Test
      fun `can create a wing location in draft form`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createWing))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "LEI",
              "code": "B",
              "pathHierarchy": "B",
              "status": "DRAFT",
              "locationType": "WING",
              "active": false,
              "key": "LEI-B",
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(0)
      }

      @Test
      fun `can create a cell location in draft form`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest.copy(prisonId = "LEI", code = "010", parentId = leedsWing.findSubLocations().find { it.getKey() == "LEI-A-1" }!!.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "LEI",
              "code": "010",
              "pathHierarchy": "A-1-010",
              "status": "DRAFT",
              "locationType": "CELL",
              "active": false,
              "key": "LEI-A-1-010",
              "inCellSanitation": true,
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "pendingChanges": {
                "maxCapacity": 2
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(0)
      }

      @Test
      fun `can create details of a location with a parent key reference`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createResidentialLocationRequest.copy(parentLocationKey = landingZ1.getKey())))
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
                "certifiedNormalAccommodation": 2
              },
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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

      @Test
      fun `can create CSWAP`() {
        webTestClient.post().uri("/locations/residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              CreateResidentialLocationRequest(
                prisonId = "ZZGHI",
                code = "CSWAP",
                locationType = ResidentialLocationType.RESIDENTIAL_UNIT,
                capacity = Capacity(maxCapacity = 90, workingCapacity = 90),
                accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
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
              "isResidential": false,
              "code": "CSWAP",
              "pathHierarchy": "CSWAP",
              "locationType": "AREA",
              "active": true,
              "key": "ZZGHI-CSWAP",
              "capacity": {
                "maxCapacity": 90,
                "workingCapacity": 90
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "ZZGHI-CSWAP",
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/{id}/change-local-name")
  @Nested
  inner class ChangeLocalNameTest {
    val localNameChange = UpdateLocationLocalNameRequest(
      localName = "Landing Z1 - CHANGED",
      updatedBy = "TEST_USER_1",
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(localNameChange))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(localNameChange))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(localNameChange))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot add a local name more that 30 characters`() {
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "localName": "1234567890123456789012345678901"} """)
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot set a location to the same local name as another location`() {
        assertThat(
          webTestClient.put().uri("/locations/${landingZ2.id}/change-local-name")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(""" { "localName": "Landing 1"} """)
            .exchange()
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.DuplicateLocalNameAtSameLevel.errorCode)
      }

      @Test
      fun `cannot set a wing to the same local name as another wing`() {
        assertThat(
          webTestClient.put().uri("/locations/${wingZ.id}/change-local-name")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(""" { "localName": "Wing B"} """)
            .exchange()
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.DuplicateLocalNameAtSameLevel.errorCode)
      }

      @Test
      fun `cannot update local name of a locked location`() {
        val aCell = repository.findOneByKey("NMI-A-1-001") as Cell

        // Attempt to update the local name of the locked location
        webTestClient.put().uri("/locations/${aCell.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "localName": "New Local Name"} """)
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody(ErrorResponse::class.java)
          .returnResult().responseBody!!.also {
          assertThat(it.errorCode).isEqualTo(ErrorCode.LockedLocationCannotBeUpdated.errorCode)
          assertThat(it.userMessage).contains("Location ${aCell.getKey()} cannot be updated as it is locked")
        }
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update details of a local name`() {
        val locationChanged = webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(localNameChange))
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(locationChanged.localName).isEqualTo("Landing Z1 - CHANGED")

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
          )
        }
      }

      @Test
      fun `can update details of a local name to null`() {
        val newLocalName = "A New Local Name"
        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{  "localName": "$newLocalName" } """)
          .exchange()
          .expectStatus().isOk

        val beforeChange = webTestClient.get().uri("/locations/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(beforeChange.localName).isEqualTo(newLocalName)

        val locationChanged = webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("{}")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(locationChanged.localName).isNull()

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
          )
        }
      }

      @Test
      fun `can update details to the same name`() {
        val locationChanged = webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{  "localName": "${landingZ1.localName}" } """)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(locationChanged.localName).isEqualTo(landingZ1.localName)

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
          )
        }
      }

      @Test
      fun `can set a location to the same local name as another location provided in a different wing`() {
        landingZ1.localName = "TEMP"
        repository.save(landingZ1)

        webTestClient.put().uri("/locations/${landingZ1.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "localName": "Landing 1"} """)
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `can set a wing to a unique local name`() {
        webTestClient.put().uri("/locations/${wingZ.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "localName": "Wing Z"} """)
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `can update details of a local name for a duplicate if perm deactivated`() {
        landingZ1.temporarilyDeactivate(
          deactivatedReason = DeactivatedReason.MOTHBALLED,
          deactivatedDate = LocalDateTime.now(clock),
          userOrSystemInContext = EXPECTED_USERNAME,
          linkedTransaction = linkedTransaction,
        )

        landingZ1.permanentlyDeactivate(
          reason = "Demolished",
          deactivatedDate = LocalDateTime.now(clock),
          userOrSystemInContext = EXPECTED_USERNAME,
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
        repository.save(landingZ1)

        val locationChanged = webTestClient.put().uri("/locations/${landingZ2.id}/change-local-name")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{  "localName": "${landingZ1.localName}" } """)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(locationChanged.localName).isEqualTo(landingZ1.localName)

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to landingZ2.getKey(),
          )
        }
      }
    }
  }

  @DisplayName("GET /locations/{prisonId}/local-name/{localName}")
  @Nested
  inner class FindLocationByLocalNameTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/${landingZ1.localName}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/${landingZ1.localName}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/${landingZ1.localName}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad local name data`() {
        webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/1234567890123456789012345678901")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `access client error bad prisonID data`() {
        webTestClient.get().uri("/locations/XXXXXXXXXX/local-name/${landingZ1.localName}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `Does not return a location when not found`() {
        webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/WIBBLE")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `Does not return a location when local name in different wing`() {
        webTestClient.get().uri("\"/locations/${landingZ1.prisonId}/local-name/${landingZ2.localName}?parentLocationId=${wingB.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isEqualTo(404)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can find a location by local name`() {
        val foundLocation = webTestClient.get().uri("/locations/${wingB.prisonId}/local-name/${wingB.localName}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(foundLocation.getKey()).isEqualTo(wingB.getKey())
      }

      @Test
      fun `can find a location by local name and wing location specified`() {
        val foundLocation = webTestClient.get().uri("/locations/${landingZ1.prisonId}/local-name/${landingZ1.localName}?parentLocationId=${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(foundLocation.getKey()).isEqualTo(landingZ1.getKey())
      }
    }
  }

  @DisplayName("PATCH /locations/residential/{id}")
  @Nested
  inner class PatchLocationTest {
    val changeCode = PatchResidentialLocationRequest(
      code = "3",
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
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
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update a locked location`() {
        val aCell = repository.findOneByKey("NMI-A-1-001") as Cell

        // Attempt to update the locked location
        webTestClient.patch().uri("/locations/residential/${aCell.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(comments = "Change comment")))
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody(ErrorResponse::class.java)
          .returnResult().responseBody!!.also {
          assertThat(it.errorCode).isEqualTo(ErrorCode.LockedLocationCannotBeUpdated.errorCode)
          assertThat(it.userMessage).contains("Location ${aCell.getKey()} cannot be updated as it is locked")
        }
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update comment`() {
        webTestClient.patch().uri("/locations/residential/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(comments = "Change comment")))
          .exchange()
          .expectStatus().isOk

        assertThat(
          webTestClient.get().uri("/sync/id/${cell1.id}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody(LegacyLocation::class.java)
            .returnResult().responseBody!!.comments,
        ).isEqualTo("Change comment")

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
          )
        }
      }

      @Test
      fun `can update details of a locations code`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
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
                },
                {
                    "prisonId": "MDI",
                    "code": "01S",
                    "pathHierarchy": "Z-3-01S",
                    "locationType": "STORE",
                    "localName": "Store Room"
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(4).let {
          assertThat(it).hasSize(4)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-3",
            "location.inside.prison.amended" to "MDI-Z-3-001",
            "location.inside.prison.amended" to "MDI-Z-3-002",
            "location.inside.prison.amended" to "MDI-Z-3-01S",
          )
        }
      }

      @Test
      fun `can update parent of a location`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
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
                "accommodationTypes": [ "NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION" ],
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true,
                  "certifiedNormalAccommodation": 4
                },
                "isResidential": true,
                "key": "MDI-B-1"
              }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 0
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
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "status": "ACTIVE",
                  "level": 2,
                  "leafLevel": true,
                  "key": "MDI-Z-ADJUDICATION",
                  "isResidential": false
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
                    "certifiedNormalAccommodation": 0
                  },
                  "childLocations": []
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 6
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
                    "certifiedNormalAccommodation": 2
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
                        "workingCapacity": 0
                      },
                      "certification": {
                        "certified": true,
                        "certifiedNormalAccommodation": 2
                      },
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "2023-12-05T12:34:56",
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
                    "certifiedNormalAccommodation": 4
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
                        "certifiedNormalAccommodation": 2
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
                        "certifiedNormalAccommodation": 2
                      }
                    },
                  {
                  "prisonId": "MDI",
                  "code": "01S",
                  "pathHierarchy": "B-1-01S",
                  "locationType": "STORE",
                  "leafLevel": true,
                  "localName": "Store Room",
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-B-1-01S"
                }
                  ]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can update parent of a location by key`() {
        webTestClient.patch().uri("/locations/residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(parentLocationKey = wingB.getKey())))
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
                "accommodationTypes": [ "NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION" ],
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true,
                  "certifiedNormalAccommodation": 4
                },
                "isResidential": true,
                "key": "MDI-B-1"
              }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 0
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
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "status": "ACTIVE",
                  "level": 2,
                  "leafLevel": true,
                  "key": "MDI-Z-ADJUDICATION",
                  "isResidential": false
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
                    "certifiedNormalAccommodation": 0
                  },
                  "childLocations": []
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 6
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
                    "certifiedNormalAccommodation": 2
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
                        "workingCapacity": 0
                      },
                      "certification": {
                        "certified": true,
                        "certifiedNormalAccommodation": 2
                      },
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "2023-12-05T12:34:56",
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
                    "certifiedNormalAccommodation": 4
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
                        "certifiedNormalAccommodation": 2
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
                        "certifiedNormalAccommodation": 2
                      }
                    },
                  {
                  "prisonId": "MDI",
                  "code": "01S",
                  "pathHierarchy": "B-1-01S",
                  "locationType": "STORE",
                  "leafLevel": true,
                  "localName": "Store Room",
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-B-1-01S"
                }
                  ]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-B",
            "location.inside.prison.amended" to "MDI-B-1",
            "location.inside.prison.amended" to "MDI-B-1-001",
            "location.inside.prison.amended" to "MDI-B-1-002",
            "location.inside.prison.amended" to "MDI-B-1-01S",
          )
        }
      }

      @Test
      fun `can update location to make top level location`() {
        webTestClient.patch().uri("/locations/residential/key/$landingZ1")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(removeParent = true, code = "Y", localName = "Wing Y", locationType = ResidentialLocationType.WING)))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
               {
                "prisonId": "MDI",
                "code": "Y",
                "pathHierarchy": "Y",
                "localName": "Wing Y",
                "locationType": "WING",
                "accommodationTypes": [ "NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION" ],
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true,
                  "certifiedNormalAccommodation": 4
                },
                "isResidential": true,
                "key": "MDI-Y"
              }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 0
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
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "status": "ACTIVE",
                  "level": 2,
                  "leafLevel": true,
                  "key": "MDI-Z-ADJUDICATION",
                  "isResidential": false
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
                    "certifiedNormalAccommodation": 0
                  },
                  "childLocations": []
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/key/MDI-Y?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "pathHierarchy": "Y",
              "locationType": "WING",
              "key": "MDI-Y",
              "localName": "Wing Y",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 4
              },
              "childLocations": [
                {
                  "key": "MDI-Y-001",
                  "pathHierarchy": "Y-001",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "status": "ACTIVE",
                  "active": true,
                  "deactivatedByParent": false,
                  "level": 2,
                  "leafLevel": true
                },
                {
                  "key": "MDI-Y-002",
                  "pathHierarchy": "Y-002",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "status": "ACTIVE",
                  "active": true,
                  "deactivatedByParent": false,
                  "level": 2,
                  "leafLevel": true
                },
                {
                  "key": "MDI-Y-01S",
                  "pathHierarchy": "Y-01S",
                  "locationType": "STORE",
                  "localName": "Store Room",
                  "permanentlyInactive": false,
                  "status": "ACTIVE",
                  "active": true,
                  "deactivatedByParent": false,
                  "level": 2,
                  "leafLevel": true
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Y",
            "location.inside.prison.amended" to "MDI-Y-001",
            "location.inside.prison.amended" to "MDI-Y-002",
            "location.inside.prison.amended" to "MDI-Y-01S",
          )
        }
      }
    }
  }

  @DisplayName("PATCH /locations/residential/key/{key}")
  @Nested
  inner class PatchLocationTestByKey {
    val changeCode = PatchResidentialLocationRequest(
      code = "3",
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
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
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update details of a locations code`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
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
                },
                {
                    "prisonId": "MDI",
                    "code": "01S",
                    "pathHierarchy": "Z-3-01S",
                    "locationType": "STORE",
                    "localName": "Store Room"
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(4).let {
          assertThat(it).hasSize(4)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-3",
            "location.inside.prison.amended" to "MDI-Z-3-001",
            "location.inside.prison.amended" to "MDI-Z-3-002",
            "location.inside.prison.amended" to "MDI-Z-3-01S",
          )
        }
      }

      @Test
      fun `can update parent of a location by key`() {
        webTestClient.patch().uri("/locations/residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PatchResidentialLocationRequest(parentLocationKey = wingB.getKey())))
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
                "accommodationTypes": [ "NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION" ],
                "capacity": {
                  "maxCapacity": 4,
                  "workingCapacity": 4
                },
                "certification": {
                  "certified": true,
                  "certifiedNormalAccommodation": 4
                },
                "isResidential": true,
                "key": "MDI-B-1"
              }
          """,
            JsonCompareMode.LENIENT,
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
              "leafLevel": false,
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
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
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "status": "ACTIVE",
                  "level": 2,
                  "leafLevel": true,
                  "key": "MDI-Z-ADJUDICATION",
                  "isResidential": false
                },
                {
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "leafLevel": false,
                  "locationType": "LANDING",
                  "key": "MDI-Z-2",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "certification": {
                    "certified": false,
                    "certifiedNormalAccommodation": 0
                  },
                  "childLocations": []
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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
                "certifiedNormalAccommodation": 6
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
                    "certifiedNormalAccommodation": 2
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
                        "workingCapacity": 0
                      },
                      "certification": {
                        "certified": true,
                        "certifiedNormalAccommodation": 2
                      },
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "2023-12-05T12:34:56",
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
                    "certifiedNormalAccommodation": 4
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
                        "certifiedNormalAccommodation": 2
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
                        "certifiedNormalAccommodation": 2
                      }
                    },
                   {
                    "prisonId": "MDI",
                    "code": "01S",
                    "pathHierarchy": "B-1-01S",
                    "locationType": "STORE",
                    "localName": "Store Room"
                }
                  ]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/convert-cell-to-non-res-cell")
  @Nested
  inner class ConvertCellToNonResCell {

    var convertCellToNonResidentialLocationRequest =
      LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(
        convertedCellType = ConvertedCellType.OTHER,
        otherConvertedCellType = "Tanning room",
      )

    var nonResStoreRoomRequest =
      LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(
        convertedCellType = ConvertedCellType.STORE,
        otherConvertedCellType = "Store Room",
      )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update convert to non residential cell as ID is not found`() {
        webTestClient.put().uri("/locations/${UUID.randomUUID()}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot convert a locked cell to non-residential cell`() {
        val aCell = repository.findOneByKey("NMI-A-1-001") as Cell

        prisonerSearchMockServer.stubSearchByLocations(aCell.prisonId, listOf(aCell.getPathHierarchy()), false)

        // Attempt to convert the locked cell to a non-residential cell
        webTestClient.put().uri("/locations/${aCell.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isEqualTo(409)
          .expectBody(ErrorResponse::class.java)
          .returnResult().responseBody!!.also {
          assertThat(it.errorCode).isEqualTo(ErrorCode.LockedLocationCannotBeUpdated.errorCode)
          assertThat(it.userMessage).contains("Location ${aCell.getKey()} cannot be updated as it is locked")
        }
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update convert cell to non res cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell1.getPathHierarchy()), false)
        val result = webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertCellToNonResidentialLocationRequest)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.convertedCellType).isEqualTo(ConvertedCellType.OTHER)
        assertThat(result.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "key": "${cell1.getKey()}",
                "changeHistory": [
                   {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Non-residential"]
                  },
                  {
                    "attribute": "Certification",
                    "oldValues": ["Certified"],
                    "newValues": ["Uncertified"]
                  },
                  {
                    "attribute": "Working capacity",
                    "oldValues": ["2"],
                    "newValues": ["None"]
                  },
                  {
                    "attribute": "Maximum capacity",
                    "oldValues": ["2"],
                    "newValues": ["None"]
                  },
                  {
                    "attribute": "Non-residential room",
                    "newValues": ["Other - Tanning room"]
                  },
                  {
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"],
                    "amendedBy": "A_TEST_USER"
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can update convert room to non res cell`() {
        assertThat(
          webTestClient.get().uri("/locations/${store.id}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody(LocationTest::class.java)
            .returnResult().responseBody!!.localName,
        ).isEqualTo(store.localName)

        prisonerSearchMockServer.stubSearchByLocations(store.prisonId, listOf(store.getPathHierarchy()), false)
        val result = webTestClient.put().uri("/locations/${store.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(nonResStoreRoomRequest)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.convertedCellType == ConvertedCellType.STORE)
        assertThat(result.otherConvertedCellType == nonResStoreRoomRequest.otherConvertedCellType)
        assertThat(result.locationType == LocationType.ROOM)
        assertThat(result.localName).isNull()
        assertThat(result.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to store.getKey(),
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${store.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "key": "${store.getKey()}",
                "changeHistory": [
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Non-residential"]
                  },
                  {
                    "attribute": "Non-residential room",
                    "newValues": ["Store room - Store Room"]
                  }
                ]
              }
            """.trimIndent(),
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/update-non-res-cell")
  @Nested
  inner class UpdateNonResCellType {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{}""")
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot update non res cell as ID is not found`() {
        webTestClient.put().uri("/locations/${UUID.randomUUID()}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot update non res cell as is not a non res cell`() {
        webTestClient.put().uri("/locations/${cell2.id!!}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isEqualTo(404)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update non-res cell type`() {
        cell1.convertToNonResidentialCell(
          convertedCellType = ConvertedCellType.OTHER,
          otherConvertedCellType = "Playroom",
          userOrSystemInContext = EXPECTED_USERNAME,
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
        repository.save(cell1)

        val result = webTestClient.put().uri("/locations/${cell1.id}/update-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "convertedCellType": "OFFICE" } """)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.findByPathHierarchy("Z-1-001")!!.convertedCellType == ConvertedCellType.OFFICE)
        Assertions.assertNull(result.findByPathHierarchy("Z-1-001")!!.otherConvertedCellType)

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
          )
        }
        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "key": "${cell1.getKey()}",
                "changeHistory": [
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Non-residential"]
                  },
                  {
                    "attribute": "Non-residential room",
                    "oldValues": ["Other - Playroom"],
                    "newValues": ["Office"]
                  },
                  {
                    "attribute": "Non-residential room",
                    "newValues": ["Other - Playroom"]
                  },
                  {
                    "attribute": "Certification",
                    "oldValues": ["Certified"],
                    "newValues": ["Uncertified"]
                  },
                  {
                    "attribute": "Maximum capacity",
                    "oldValues": ["2"]
                  },
                  {
                    "attribute": "Working capacity",
                    "oldValues": ["2"]
                  },
                  {
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"]
                  }
                ]
              }
            """.trimIndent(),
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/convert-to-cell")
  @Nested
  inner class ConvertToCellTest {
    var convertToCellRequest = LocationResidentialResource.ConvertToCellRequest(
      accommodationType = AllowedAccommodationTypeForConversion.NORMAL_ACCOMMODATION,
      specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL, SpecialistCellType.ISOLATION_DISEASES),
      maxCapacity = 2,
      workingCapacity = 2,
    )

    var convertToCellRequestNotValidMaxCapacity = LocationResidentialResource.ConvertToCellRequest(
      accommodationType = AllowedAccommodationTypeForConversion.CARE_AND_SEPARATION,
      specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL),
      maxCapacity = -1,
      workingCapacity = 2,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    private var convertToCellRequestNotValidWorkingCapacity = LocationResidentialResource.ConvertToCellRequest(
      accommodationType = AllowedAccommodationTypeForConversion.CARE_AND_SEPARATION,
      specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL),
      maxCapacity = 1,
      workingCapacity = -1,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    private var convertToCellRequestValidCareAndSeparation = LocationResidentialResource.ConvertToCellRequest(
      accommodationType = AllowedAccommodationTypeForConversion.CARE_AND_SEPARATION,
      specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL),
      maxCapacity = 2,
      workingCapacity = 2,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    private var convertToCellRequestValidHealthCareInpatients = LocationResidentialResource.ConvertToCellRequest(
      accommodationType = AllowedAccommodationTypeForConversion.HEALTHCARE_INPATIENTS,
      specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL),
      maxCapacity = 2,
      workingCapacity = 2,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update convert to cell as Location ID is not found`() {
        webTestClient.put().uri("/locations/${UUID.randomUUID()}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot update convert to cell as request has invalid Max Capacity `() {
        cell1.convertToNonResidentialCell(
          convertedCellType = ConvertedCellType.OTHER,
          userOrSystemInContext = "Aleman",
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
        repository.save(cell1)
        // request has not valid data
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequestNotValidMaxCapacity)
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot convert non-res cell with accommodation type other non residential to residential cell`() {
        cell1.convertToNonResidentialCell(
          convertedCellType = ConvertedCellType.OTHER,
          userOrSystemInContext = "Aleman",
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
        repository.save(cell1)

        val response = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            """
            {
                  "accommodationType": "OTHER_NON_RESIDENTIAL",
                  "specialistCellType": "ACCESSIBLE_CELL",
                  "maxCapacity": 2,
                  "workingCapacity": 2,
                  "usedForTypes": ["STANDARD_ACCOMMODATION", "PERSONALITY_DISORDER"]
            }
            """.trimIndent(),
          )
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody(ErrorResponse::class.java)
          .returnResult().responseBody!!

        assertThat(response.userMessage).contains("not one of the values accepted for Enum class: [NORMAL_ACCOMMODATION, CARE_AND_SEPARATION, HEALTHCARE_INPATIENTS]")
      }

      @Test
      fun `cannot update convert to cell as request has invalid Working Capacity `() { // request has not valid data
        cell1.convertToNonResidentialCell(
          convertedCellType = ConvertedCellType.OTHER,
          userOrSystemInContext = "Aleman",
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
        repository.save(cell1)

        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequestNotValidWorkingCapacity)
          .exchange()
          .expectStatus().isEqualTo(400)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can convert non-res cell to res cell`() {
        cell1.convertToNonResidentialCell(
          convertedCellType = ConvertedCellType.OTHER,
          userOrSystemInContext = "Aleman",
          clock = clock,
          linkedTransaction = linkedTransactionRepository.saveAndFlush(
            LinkedTransaction(
              prisonId = cell1.prisonId,
              transactionType = TransactionType.LOCATION_CREATE,
              transactionDetail = "Convert to Non Res before test runs",
              transactionInvokedBy = "Aleman",
              txStartTime = LocalDateTime.now(clock).minusMinutes(5),
            ),
          ),
        )
        repository.save(cell1)

        val result = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequest)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        val cellZ1001 = result.findByPathHierarchy("Z-1-001") ?: throw LocationNotFoundException("Z-1-001")
        assertThat(cellZ1001.capacity?.maxCapacity).isEqualTo(2)
        assertThat(cellZ1001.capacity?.workingCapacity).isEqualTo(2)
        assertThat(cellZ1001.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.ACCESSIBLE_CELL, SpecialistCellType.ISOLATION_DISEASES)
        assertThat(cellZ1001.convertedCellType).isNotEqualTo(ConvertedCellType.OTHER)

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.changeHistory[0].attribute").isEqualTo("Status")
          .jsonPath("$.changeHistory[1].attribute").isEqualTo("Certification")
          .jsonPath("$.changeHistory[2].attribute").isEqualTo("Used for")
          .jsonPath("$.changeHistory[3].attribute").isEqualTo("Cell type")
          .jsonPath("$.changeHistory[4].attribute").isEqualTo("Working capacity")
          .jsonPath("$.changeHistory[5].attribute").isEqualTo("Maximum capacity")

        val tx = webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!.changeHistory?.firstOrNull()?.transactionId
        assertThat(tx).isNotNull()

        webTestClient.get().uri("/transactions/$tx")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.transactionType").isEqualTo("ROOM_CONVERTION_TO_CELL")
          .jsonPath("$.prisonId").isEqualTo(cell1.prisonId)
          .jsonPath("$.transactionDetails[0].attribute").isEqualTo("Status")
          .jsonPath("$.transactionDetails[1].attribute").isEqualTo("Certification")
          .jsonPath("$.transactionDetails[2].attribute").isEqualTo("Used for")
          .jsonPath("$.transactionDetails[3].attribute").isEqualTo("Cell type")
          .jsonPath("$.transactionDetails[4].attribute").isEqualTo("Working capacity")
          .jsonPath("$.transactionDetails[5].attribute").isEqualTo("Maximum capacity")

        assertThat(
          webTestClient.get().uri("/sync/id/${cell1.id}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk()
            .expectBody(LegacyLocation::class.java)
            .returnResult().responseBody!!.residentialHousingType,
        ).isEqualTo(ResidentialHousingType.NORMAL_ACCOMMODATION)
      }
    }

    @Test
    fun `can convert non-res cell to res cell for Care and Separation`() {
      cell1.convertToNonResidentialCell(
        convertedCellType = ConvertedCellType.OTHER,
        userOrSystemInContext = "Aleman",
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
      repository.save(cell1)

      val result = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(convertToCellRequestValidCareAndSeparation)
        .exchange()
        .expectStatus().isOk
        .expectBody(LocationTest::class.java)
        .returnResult().responseBody!!

      val cellZ1001 = result.findByPathHierarchy("Z-1-001") ?: throw LocationNotFoundException("Z-1-001")
      assertThat(cellZ1001.capacity?.maxCapacity).isEqualTo(2)
      assertThat(cellZ1001.capacity?.workingCapacity).isEqualTo(2)
      assertThat(cellZ1001.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.ACCESSIBLE_CELL)
      assertThat(cellZ1001.convertedCellType).isNotEqualTo(ConvertedCellType.OTHER)
      assertThat(cellZ1001.status == DerivedLocationStatus.ACTIVE)

      getDomainEvents(3).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to "MDI-Z-1-001",
          "location.inside.prison.amended" to "MDI-Z-1",
          "location.inside.prison.amended" to "MDI-Z",
        )
      }
    }

    @Test
    fun `can convert non-res cell to res cell for Health inpatients`() {
      cell1.convertToNonResidentialCell(
        convertedCellType = ConvertedCellType.OTHER,
        userOrSystemInContext = "Aleman",
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
      repository.save(cell1)

      val result = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(convertToCellRequestValidHealthCareInpatients)
        .exchange()
        .expectStatus().isOk
        .expectBody(LocationTest::class.java)
        .returnResult().responseBody!!

      val cellZ1001 = result.findByPathHierarchy("Z-1-001") ?: throw LocationNotFoundException("Z-1-001")
      assertThat(cellZ1001.capacity?.maxCapacity).isEqualTo(2)
      assertThat(cellZ1001.capacity?.workingCapacity).isEqualTo(2)
      assertThat(cellZ1001.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.ACCESSIBLE_CELL)
      assertThat(cellZ1001.convertedCellType).isNotEqualTo(ConvertedCellType.OTHER)

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
