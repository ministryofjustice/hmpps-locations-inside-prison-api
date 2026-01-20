package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.BasicTemporaryDeactivationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DATE_FORMAT
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationResourceIntTest : CommonDataTestBase() {

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
        webTestClient.get().uri("/locations?size=1&sort=pathHierarchy,desc")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {
              "content": [
                {
                  "key": "MDI-Z-VISIT",
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "ignoreWorkingCapacity": false,
                  "usage": [
                    {
                      "usageType": "VISIT",
                      "capacity": 15,
                      "sequence": 1
                    },
                    {
                      "usageType": "PROPERTY",
                      "capacity": 10,
                      "sequence": 99
                    },
                    {
                      "usageType": "OTHER",
                      "capacity": 10,
                      "sequence": 99
                    }
                  ],
                  "orderWithinParentLocation": 99,
                  "active": true,
                  "permanentlyDeactivated": false,
                  "lastModifiedBy": "DIFFERENT_USER",
                  "lastModifiedDate": "2023-12-04T12:34:56"
                }
              ],
              "pageable": {
                "pageNumber": 0,
                "pageSize": 1,
                "sort": {
                  "empty": false,
                  "unsorted": false,
                  "sorted": true
                },
                "offset": 0,
                "paged": true,
                "unpaged": false
              },
              "last": false,
              "totalElements": 26,
              "totalPages": 26,
              "first": true,
              "size": 1,
              "number": 0,
              "sort": {
                "empty": false,
                "unsorted": false,
                "sorted": true
              },
              "numberOfElements": 1,
              "empty": false
            }

              """,
            JsonCompareMode.LENIENT,
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
    inner class Validation {

      @Test
      fun `location id not found`() {
        webTestClient.get().uri("/locations/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().is4xxClientError
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
              ],
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "leafLevel": true,
                  "level": 2,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": true,
                  "leafLevel": true,
                  "level": 2,
                  "key": "MDI-Z-ADJUDICATION"
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
                    "certifiedNormalAccommodation": 4
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
                        "certifiedNormalAccommodation": 2
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
                        "certifiedNormalAccommodation": 2
                      }
                    },
                    {
                      "prisonId": "MDI",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE"
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
                    "certifiedNormalAccommodation": 0
                  },
                  
                  "active": true,
                  "childLocations": [],
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
      fun `can retrieve details of a CSWAP location`() {
        webTestClient.get().uri("/locations/${cswap.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "CSWAP",
              "pathHierarchy": "CSWAP",
              "locationType": "AREA",
              "active": true,
              "key": "MDI-CSWAP",
              "capacity": {
                "maxCapacity": 99,
                "workingCapacity": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )
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
      fun `bad data causes 400`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"deactivationReason": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `does not allow a reason more than 255 characters`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, deactivationReasonDescription = randomString(256))))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot deactivated location with other reason without a free text value`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER)))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(118)
      }

      @Test
      fun `cannot update a deactivated location with other reason without a free text value`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER)))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(118)
      }

      @Test
      fun `cannot deactivate a location when prisoner is inside the cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), true)

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.DeactivationErrorLocationsContainPrisoners.errorCode)
      }

      @Test
      fun `cannot deactivate a wing when prisoners are in cells below`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), true)

        assertThat(
          webTestClient.put().uri("/locations/${wingZ.id}/deactivate/permanent")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.DeactivationErrorLocationsContainPrisoners.errorCode)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a location`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Cell destroyed")))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to cell1.getKey(),
          )
        }

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, deactivationReasonDescription = "Window smashed", proposedReactivationDate = proposedReactivationDate, planetFmReference = "222333")))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
              "accommodationTypes":["CARE_AND_SEPARATION"],
              "permanentlyInactive": false,
              "proposedReactivationDate": "$proposedReactivationDate",
              "deactivatedDate": "$now",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 2
              },
              "changeHistory": [
                {
                  "transactionType": "DEACTIVATION",
                  "attribute": "Planet FM reference number",
                  "newValues": ["222333"]
                },
                { 
                  "transactionType": "DEACTIVATION",
                  "attribute": "Estimated reactivation date",
                  "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                },
                {
                  "attribute": "Deactivation reason",
                  "newValues": ["Damage - Window smashed"]
                },
                {
                  "attribute": "Status",
                  "oldValues": ["Active"],
                  "newValues": ["Inactive"]
                }
              ],
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "deactivatedByParent": false,
                  "permanentlyInactive": false,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": true,
                  "deactivatedByParent": false,
                  "permanentlyInactive": false,
                  "isResidential": false,
                  "key": "MDI-Z-ADJUDICATION"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["CARE_AND_SEPARATION"],
                  "active": false,
                  "deactivatedByParent": false,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "changeHistory": [
                    {
                      "attribute": "Status",
                      "oldValues": ["Active"],
                      "newValues": ["Inactive"]
                    },
                    {
                      "attribute": "Deactivation reason",
                      "newValues": ["Damage - Window smashed"]
                    },
                    {
                      "transactionType": "DEACTIVATION",
                      "attribute": "Planet FM reference number",
                      "newValues": ["222333"]
                    },
                    { 
                      "transactionType": "DEACTIVATION",
                      "attribute": "Estimated reactivation date",
                      "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                    }
                  ],
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["CARE_AND_SEPARATION"],
                      "active": false,
                      "deactivatedByParent": false,
                      "oldWorkingCapacity": 2,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002",
                      "changeHistory": [
                          {
                            "transactionType": "DEACTIVATION",
                            "attribute": "Planet FM reference number",
                            "newValues": ["222333"]
                          },
                          { 
                            "transactionType": "DEACTIVATION",
                            "attribute": "Estimated reactivation date",
                            "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                          },
                          {
                            "attribute": "Deactivation reason",
                            "newValues": ["Damage - Window smashed"]
                          },
                          {
                            "attribute": "Status",
                            "oldValues": ["Active"],
                            "newValues": ["Inactive"]
                          },
                          {
                            "attribute": "Working capacity",
                            "oldValues": ["2"],
                            "newValues": ["0"]
                          },
                          {
                            "attribute": "Used for",
                            "newValues": ["Standard accommodation"]
                          },
                          {
                            "attribute": "Cell type",
                            "newValues": ["Accessible cell"]
                          }
                       ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "localName": "Store Room",
                      "permanentlyInactive": false,
                      "status": "ACTIVE",
                      "active": true,
                      "deactivatedByParent": false,
                      "level": 3,
                      "leafLevel": true,
                      "key": "MDI-Z-1-01S",
                      "isResidential": true
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
                  "key": "MDI-Z-2",
                   "changeHistory": [
                          {
                            "transactionType": "DEACTIVATION",
                            "attribute": "Planet FM reference number",
                            "newValues": ["222333"]
                          },
                          { 
                            "transactionType": "DEACTIVATION",
                            "attribute": "Estimated reactivation date",
                            "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                          },
                          {
                            "attribute": "Deactivation reason",
                            "newValues": ["Damage - Window smashed"]
                          },
                          {
                            "attribute": "Status",
                            "oldValues": ["Active"],
                            "newValues": ["Inactive"]
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
      fun `can deactivate CSWAP location`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cswap.getPathHierarchy()), false)
        webTestClient.put().uri("/locations/${cswap.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = "Not Needed")))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-CSWAP",
          )
        }

        webTestClient.get().uri("/locations/${cswap.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "key": "${cswap.getKey()}",
                "deactivatedReason": "OTHER",
                "deactivationReasonDescription": "Not Needed",
                "capacity" : {
                  "workingCapacity": 0,
                  "maxCapacity": 99
                },
                "changeHistory": [
                  {
                    "attribute": "Deactivation reason",
                    "newValues": ["Other - Not Needed"]
                  },
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  }
                ]
              }
            """.trimIndent(),
          )
      }

      @Test
      fun `can deactivate a location with other reason`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = "Not Needed", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
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
                "deactivatedReason": "OTHER",
                "deactivationReasonDescription": "Not Needed",
               "changeHistory": [
                 {
                   "attribute": "Estimated reactivation date",
                   "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                 },
                  {
                    "attribute": "Deactivation reason",
                    "newValues": ["Other - Not Needed"]
                  },
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  },
                  {
                    "attribute": "Working capacity",
                    "oldValues": ["2"],
                    "newValues": ["0"]
                  },
                  {
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"],
                    "amendedBy": "A_TEST_USER"
                  }
                ]
              }
            """.trimIndent(),
          )
      }

      @Test
      fun `can deactivate a location with other reason that is blank`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, deactivationReasonDescription = "", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
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
                "deactivatedReason": "MOTHBALLED",
                "deactivationReasonDescription": "",
                 "changeHistory": [
                  {
                    "attribute": "Estimated reactivation date",
                    "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                  },
                  {
                    "attribute": "Deactivation reason",
                    "newValues": ["Mothballed"]
                  },
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  },
                  {
                    "attribute": "Working capacity",
                    "oldValues": ["2"],
                    "newValues": ["0"]
                  },
                  {
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"],
                    "amendedBy": "A_TEST_USER"
                  }
                ]
              }
            """.trimIndent(),
          )
      }

      @Test
      fun `can deactivate a location with other reason that is 255 characters`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, deactivationReasonDescription = randomString(255))))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3)
      }

      @Test
      fun `can update a deactivated location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
          )
        }
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, deactivationReasonDescription = "Spiders", planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
          )
        }

        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
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
                "oldWorkingCapacity": 2,
                "deactivatedByParent": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "deactivatedDate": "$now",
                "deactivatedReason": "MOTHBALLED",
                "deactivationReasonDescription": "Spiders",
                "planetFmReference": "334423",
                "isResidential": true,
                "key": "MDI-Z-1-001",
                "changeHistory": [
                  {
                    "transactionType": "LOCATION_CREATE",
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"]
                  },
                  {
                    "transactionType": "DEACTIVATION",
                    "attribute": "Working capacity",
                    "oldValues": ["2"],
                    "newValues": ["0"]
                  },
                  {
                    "transactionType": "DEACTIVATION",
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  },
                  {
                    "transactionType": "DEACTIVATION",
                    "attribute": "Deactivation reason",
                    "newValues": ["Damage"]
                  },
                  {
                    "transactionType": "LOCATION_UPDATE",
                    "attribute": "Deactivation reason",
                    "oldValues": ["Damage"],
                    "newValues": ["Mothballed - Spiders"]
                  },
                  {
                    "transactionType": "LOCATION_UPDATE",
                    "attribute": "Estimated reactivation date",
                    "newValues": ["05/01/2024"]
                  },
                  {
                    "transactionType": "LOCATION_UPDATE",
                    "attribute": "Planet FM reference number",
                    "newValues": ["334423"]
                  }
                ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can update a deactivated location with other reason`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, deactivationReasonDescription = "Water damage")))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
          )
        }

        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = "Poor state", planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
          )
        }

        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
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
                "oldWorkingCapacity": 2,
                "deactivatedByParent": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "deactivatedDate": "$now",
                "deactivatedReason": "OTHER",
                "deactivationReasonDescription": "Poor state",
                "planetFmReference": "334423",
                "isResidential": true,
                "key": "MDI-Z-1-001",
                "changeHistory": [
                  {
                    "attribute": "Estimated reactivation date",
                    "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                  },
                  {
                    "attribute": "Planet FM reference number",
                    "newValues": ["334423"]
                  },
                  {
                    "attribute": "Deactivation reason",
                    "oldValues": ["Damage - Water damage"],
                    "newValues": ["Other - Poor state"]
                  },
                  {
                    "attribute": "Deactivation reason",
                    "newValues": ["Damage - Water damage"]
                  },
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  },
                  {
                    "attribute": "Working capacity",
                    "oldValues": ["2"],
                    "newValues": ["0"]
                  },
                  {
                    "attribute": "Used for",
                    "newValues": ["Standard accommodation"],
                    "amendedBy": "A_TEST_USER"
                  }
                ]
            }
          """,
            JsonCompareMode.LENIENT,
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

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }

        webTestClient.put().uri("/locations/${wingZ.id}/reactivate?cascade-reactivation=true")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(7).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
                "certifiedNormalAccommodation": 4
              },
              "changeHistory": [
                {
                  "attribute": "Status",
                  "oldValues": ["Inactive"],
                  "newValues": ["Active"]
                },
                {
                  "attribute": "Deactivation reason",
                  "newValues": ["Mothballed"]
                },
                {
                  "attribute": "Status",
                  "oldValues": ["Active"],
                  "newValues": ["Inactive"]
                }
              ],
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
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-ADJUDICATION"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION"],
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
                      "key": "MDI-Z-1-001",
                      "changeHistory": [
                        {
                          "attribute": "Status",
                          "oldValues": ["Inactive"],
                          "newValues": ["Active"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["0"],
                          "newValues": ["2"]
                        },
                        {
                          "attribute": "Deactivation reason",
                          "newValues": ["Mothballed"]
                        },
                        {
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"],
                          "amendedBy": "A_TEST_USER"
                        }
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["CARE_AND_SEPARATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-01S"
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
            JsonCompareMode.LENIENT,
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

        assertThat(
          webTestClient.get().uri("/locations/${cell1.id}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody<LocationTest>()
            .returnResult().responseBody!!.deactivatedReason,
        ).isEqualTo(DeactivatedReason.DAMAGED)

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy(), cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, proposedReactivationDate = proposedReactivationDate.plusMonths(1), planetFmReference = "123456")))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }

        val cellDetails = webTestClient.get().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody<LocationTest>()
          .returnResult().responseBody!!
        assertThat(cellDetails.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
        assertThat(cellDetails.proposedReactivationDate).isEqualTo(proposedReactivationDate.plusMonths(1))
        assertThat(cellDetails.planetFmReference).isEqualTo("123456")

        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
                "certifiedNormalAccommodation": 4
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
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-ADJUDICATION"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION"],
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
                      "key": "MDI-Z-1-001",
                      "changeHistory": [
                        {
                          "transactionType": "REACTIVATION",
                          "attribute": "Planet FM reference number",
                          "oldValues": ["${cellDetails.planetFmReference}"]
                        },
                        {
                          "transactionType": "REACTIVATION",
                          "attribute": "Estimated reactivation date",
                          "oldValues": ["${cellDetails.proposedReactivationDate?.format(DATE_FORMAT)}"]
                        },
                        {
                          "transactionType": "REACTIVATION",
                          "attribute": "Status",
                          "oldValues": ["Inactive"],
                          "newValues": ["Active"]
                        },
                        {
                          "transactionType": "REACTIVATION",
                          "attribute": "Working capacity",
                          "oldValues": ["0"],
                          "newValues": ["2"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Planet FM reference number",
                          "newValues": ["${cellDetails.planetFmReference}"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Estimated reactivation date",
                          "oldValues": ["${proposedReactivationDate?.format(DATE_FORMAT)}"],
                          "newValues": ["${cellDetails.proposedReactivationDate?.format(DATE_FORMAT)}"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Deactivation reason",
                          "oldValues": ["Damage"],
                          "newValues": ["Mothballed"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Estimated reactivation date",
                          "newValues": ["${proposedReactivationDate.format(DATE_FORMAT)}"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Deactivation reason",
                          "newValues": ["Damage"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "transactionType": "DEACTIVATION",
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "transactionType": "LOCATION_CREATE",
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"],
                          "amendedBy": "A_TEST_USER"
                        }
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["CARE_AND_SEPARATION"],
                      "active": false,
                      "oldWorkingCapacity": 2,
                      "deactivatedReason": "MOTHBALLED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-01S"
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
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can reactivate CSWAP`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cswap.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cswap.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-CSWAP",
          )
        }

        webTestClient.put().uri("/locations/${cswap.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-CSWAP",
          )
        }

        webTestClient.get().uri("/locations/${cswap.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "key": "MDI-CSWAP",
                "locationType": "AREA",
                "permanentlyInactive": false,
                "capacity": {
                  "maxCapacity": 99,
                  "workingCapacity": 0
                },
                "status": "ACTIVE",
                "active": true,
                "level": 1,
                "leafLevel": false,
                "changeHistory": [
                  {
                    "attribute": "Status",
                    "oldValues": ["Inactive"],
                    "newValues": ["Active"]
                  },
                  {
                    "attribute": "Deactivation reason",
                    "newValues": ["Damage"]
                  },
                  {
                    "attribute": "Status",
                    "oldValues": ["Active"],
                    "newValues": ["Inactive"]
                  }
                ],
                "isResidential": false
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/bulk/deactivate/temporary")
  @Nested
  inner class BulkTemporarilyDeactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${cell1.id!!}": { "deactivationReason": "DAMAGED" } }}""")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${cell1.id!!}": { "deactivationReason": "DAMAGED" } }}""")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${cell1.id!!}": { "deactivationReason": "DAMAGED" } }}""")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${cell1.id!!}": { "deactivationReason": "DAMAGEDXX" } }}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot deactivated location with other reason without a free text value`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/temporary")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                DeactivateLocationsRequest(
                  mapOf(
                    cell1.id!! to BasicTemporaryDeactivationRequest(
                      deactivationReason = DeactivatedReason.OTHER,
                    ),
                  ),
                ),
              ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(118)
      }

      @Test
      fun `cannot deactivated location with other reason with a blank free text value`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/temporary")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(cell1.id!! to BasicTemporaryDeactivationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = " ")))))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(118)
      }

      @Test
      fun `cannot deactivate a location when prisoner is inside the cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), true)

        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/temporary")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(cell1.id!! to BasicTemporaryDeactivationRequest(deactivationReason = DeactivatedReason.DAMAGED)))))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(109)
      }

      @Test
      fun `cannot deactivate a wing when prisoners are in cells below`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), true)

        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/temporary")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(wingZ.id!! to BasicTemporaryDeactivationRequest(deactivationReason = DeactivatedReason.DAMAGED)))))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(109)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a set of locations`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations(cell2.prisonId, listOf(cell2.getPathHierarchy()), false)
        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()

        webTestClient.put().uri("/locations/bulk/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              DeactivateLocationsRequest(
                updatedBy = "DEACTIVATING_USER",
                locations = mapOf(
                  cell1.id!! to BasicTemporaryDeactivationRequest(deactivationReason = DeactivatedReason.DAMAGED, deactivationReasonDescription = "Window smashed", proposedReactivationDate = proposedReactivationDate),
                  cell2.id!! to BasicTemporaryDeactivationRequest(deactivationReason = DeactivatedReason.REFURBISHMENT, deactivationReasonDescription = "Fire", planetFmReference = "XXX122"),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              [
                {
                  "id": "${cell1.id!!}",
                  "prisonId": "MDI",
                  "code": "001",
                  "pathHierarchy": "Z-1-001",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "oldWorkingCapacity": 2,
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
                  "deactivatedBy": "DEACTIVATING_USER",
                  "lastModifiedBy": "DEACTIVATING_USER",
                  "status": "INACTIVE",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "deactivationReasonDescription": "Window smashed",
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "topLevelId": "${wingZ.id!!}",
                  "level": 3,
                  "leafLevel": true,
                  "parentId": "${landingZ1.id!!}",
                  "key": "MDI-Z-1-001",
                  "isResidential": true
                },
                {
                  "id": "${cell2.id!!}",
                  "prisonId": "MDI",
                  "code": "002",
                  "pathHierarchy": "Z-1-002",
                  "locationType": "CELL",
                  "permanentlyInactive": false,
                  "capacity": {
                    "maxCapacity": 2,
                    "workingCapacity": 0
                  },
                  "oldWorkingCapacity": 2,
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
                  "status": "INACTIVE",
                  "active": false,
                  "deactivatedBy": "DEACTIVATING_USER",
                  "lastModifiedBy": "DEACTIVATING_USER",
                  "deactivatedByParent": false,
                  "deactivatedDate": "$now",
                  "deactivatedReason": "REFURBISHMENT",
                  "deactivationReasonDescription": "Fire",
                  "planetFmReference": "XXX122",
                   "topLevelId": "${wingZ.id!!}",
                  "level": 3,
                  "leafLevel": true,
                  "parentId": "${landingZ1.id!!}",
                  "key": "MDI-Z-1-002",
                  "isResidential": true
                }
              ]
              """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/bulk/reactivate")
  @Nested
  inner class BulkReactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/bulk/reactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.id!!}": { "cascadeReactivation": false } }}""")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.id!!}": { "cascadeReactivation": false } }}""")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.id!!}": { "cascadeReactivation": false } }}""")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can reactivate a number of locations`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy(), cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
             "key": "MDI-Z",
              "active": false,
              "deactivatedReason": "DAMAGED",
              "changeHistory": [
                {
                  "attribute": "Deactivation reason",
                  "newValues": ["Damage"]
                },
                {
                  "attribute": "Status",
                  "oldValues": ["Active"],
                  "newValues": ["Inactive"]
                }
              ],
              "childLocations": [
                {
                  "key": "MDI-Z-VISIT",
                  "active": true
                },
                {
                  "key": "MDI-Z-ADJUDICATION",
                  "active": true
                },
                {
                  "key": "MDI-Z-1",
                  "active": false,
                  "deactivatedReason": "DAMAGED",
                  "childLocations": [
                    {
                      "key": "MDI-Z-1-001",
                      "active": false,
                      "deactivatedReason": "DAMAGED",
                      "changeHistory": [
                        {
                          "attribute": "Deactivation reason",
                          "newValues": ["Damage"]
                        },
                        {
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"]
                        }
                      ]
                    },
                    {
                      "key": "MDI-Z-1-002", 
                      "active": false,
                      "deactivatedReason": "DAMAGED",
                      "changeHistory": [
                       {
                          "attribute": "Deactivation reason",
                          "newValues": ["Damage"]
                        },
                        {
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"]
                        },
                        {
                          "attribute": "Cell type",
                          "newValues": ["Accessible cell"]
                        }
                      ]
                    },
                    {
                      "key": "MDI-Z-1-01S",
                      "active": true
                    }
                  ]
                },
                {
                  "key": "MDI-Z-2",
                  "active": false,
                  "deactivatedReason": "DAMAGED"
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ReactivateLocationsRequest(
                locations = mapOf(
                  cell1.id!! to ReactivationDetail(capacity = Capacity(maxCapacity = 3, workingCapacity = 3)),
                  cell2.id!! to ReactivationDetail(),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        getDomainEvents(7).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1-002",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true&includeHistory=true")
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
                "maxCapacity": 5,
                "workingCapacity": 5
              },
              "certification": {
                "certifiedNormalAccommodation": 4
              },
              "changeHistory": [
                {
                  "attribute": "Status",
                  "oldValues": ["Inactive"],
                  "newValues": ["Active"]
                },
                {
                  "attribute": "Deactivation reason",
                  "newValues": ["Damage"]
                },
                {
                  "attribute": "Status",
                  "oldValues": ["Active"],
                  "newValues": ["Inactive"]
                }
              ],
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
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-ADJUDICATION"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION", "CARE_AND_SEPARATION"],
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
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 3,
                        "workingCapacity": 3
                      },
                      "changeHistory": [
                        {
                          "attribute": "Status",
                          "oldValues": ["Inactive"],
                          "newValues": ["Active"]
                        },
                        {
                          "attribute": "Maximum capacity",
                          "oldValues": ["2"],
                          "newValues": ["3"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["0"],
                          "newValues": ["3"]
                        },
                        {
                          "attribute": "Deactivation reason",
                          "newValues": ["Damage"]
                        },
                        {
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"]
                        }
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["CARE_AND_SEPARATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      },
                      "changeHistory": [
                          {
                          "attribute": "Status",
                          "oldValues": ["Inactive"],
                          "newValues": ["Active"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["0"],
                          "newValues": ["2"]
                        },
                       {
                          "attribute": "Deactivation reason",
                          "newValues": ["Damage"]
                        },
                        {
                          "attribute": "Status",
                          "oldValues": ["Active"],
                          "newValues": ["Inactive"]
                        },
                        {
                          "attribute": "Working capacity",
                          "oldValues": ["2"],
                          "newValues": ["0"]
                        },
                        {
                          "attribute": "Used for",
                          "newValues": ["Standard accommodation"]
                        },
                        {
                          "attribute": "Cell type",
                          "newValues": ["Accessible cell"]
                        }
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-01S"
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
                  "deactivatedReason": "DAMAGED",
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ReactivateLocationsRequest(
                locations = mapOf(
                  landingZ1.id!! to ReactivationDetail(cascadeReactivation = true),
                  landingZ2.id!! to ReactivationDetail(),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
             "key": "MDI-Z",
              "active": true,
              "childLocations": [
                {
                  "key": "MDI-Z-VISIT",
                  "active": true
                },
                {
                  "key": "MDI-Z-ADJUDICATION",
                  "active": true
                },
                {
                  "key": "MDI-Z-1",
                  "active": true,
                  "childLocations": [
                    {
                      "key": "MDI-Z-1-001",
                      "active": true
                    },
                    {
                      "key": "MDI-Z-1-002", 
                      "active": true
                    },
                    {
                      "key": "MDI-Z-1-01S",
                      "active": true
                    }
                  ]
                },
                {
                  "key": "MDI-Z-2",
                  "active": true
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(2).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }

      @Test
      fun `can reactivate a number of locations and update capacity in cascade locations`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy(), cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }

        webTestClient.put().uri("/locations/bulk/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ReactivateLocationsRequest(
                locations = mapOf(
                  wingZ.id!! to ReactivationDetail(cascadeReactivation = true),
                  landingZ2.id!! to ReactivationDetail(),
                  cell2.id!! to ReactivationDetail(capacity = Capacity(maxCapacity = 5, workingCapacity = 4)),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        getDomainEvents(8).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to cell1.getKey(),
            "location.inside.prison.reactivated" to cell2.getKey(),
            "location.inside.prison.reactivated" to landingZ2.getKey(),
            "location.inside.prison.reactivated" to landingZ1.getKey(),
            "location.inside.prison.reactivated" to wingZ.getKey(),
            "location.inside.prison.amended" to cell2.getKey(),
            "location.inside.prison.amended" to landingZ1.getKey(),
            "location.inside.prison.amended" to wingZ.getKey(),
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
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 7,
                "workingCapacity": 6
              },
              "childLocations": [
                {
                  "key": "MDI-Z-VISIT"
                },
                {
                  "key": "MDI-Z-ADJUDICATION"
                },
                {
                  "key": "MDI-Z-1",
                  "capacity": {
                    "maxCapacity": 7,
                    "workingCapacity": 6
                  },
                  "childLocations": [
                    {
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      }
                    },
                    {
                      "key": "MDI-Z-1-002",
                      "capacity": {
                        "maxCapacity": 5,
                        "workingCapacity": 4
                      }
                    },
                    {
                      "key": "MDI-Z-1-01S",
                      "capacity": {
                        "maxCapacity": 0,
                        "workingCapacity": 0
                      }
                    }
                  ]
                },
                {
                  "key": "MDI-Z-2",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  }
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/bulk/update")
  @Nested
  inner class BulkCapacityLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/bulk/update")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/bulk/capacity-update")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.getKey()}": { "maxCapacity": 1, "workingCapacity": 1 } }}""")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/bulk/capacity-update")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.getKey()}": { "maxCapacity": 1, "workingCapacity": 1 } }}""").exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/bulk/capacity-update")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue("""{ "locations": { "${wingZ.getKey()}": { "maxCapacity": 1, "workingCapacity": 1 } }}""").exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can bulk update a cell capacities`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations(cell2.prisonId, listOf(cell2.getPathHierarchy()), true)
        prisonerSearchMockServer.stubSearchByLocations(cell1N.prisonId, listOf(cell1N.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations("MDI", listOf("1-2-008"), false)
        prisonerSearchMockServer.stubSearchByLocations(archivedCell.prisonId, listOf(archivedCell.getPathHierarchy()), false)
        prisonerSearchMockServer.stubSearchByLocations(inactiveCellB3001.prisonId, listOf(inactiveCellB3001.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/bulk/capacity-update")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              UpdateCapacityRequest(
                locations = mapOf(
                  cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3, certifiedNormalAccommodation = 3, inCellSanitation = true, cellMark = "X001"),
                  cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 0, workingCapacity = 0, certifiedNormalAccommodation = 0, inCellSanitation = null, cellMark = "X002"),
                  cell1N.getKey() to CellCapacityUpdateDetail(maxCapacity = 4, workingCapacity = 1, inCellSanitation = true, cellMark = "X001-N"),
                  "MDI-1-2-008" to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3, cellMark = "X008"),
                  archivedCell.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3, inCellSanitation = false, cellMark = "X001-ARCH"),
                  inactiveCellB3001.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3, certifiedNormalAccommodation = 3),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "MDI-Z-1-001": [
                  {
                    "key": "MDI-Z-1-001",
                    "message": "Max capacity from 2 ==> 3",
                    "type": "maxCapacity",
                    "previousValue": 2,
                    "newValue": 3
                  },
                  {
                    "key": "MDI-Z-1-001",
                    "message": "Working capacity from 2 ==> 3",
                    "type": "workingCapacity",
                    "previousValue": 2,
                    "newValue": 3
                  },
                  {
                    "key": "MDI-Z-1-001",
                    "message": "Baseline CNA from 2 ==> 3",
                    "type": "CNA",
                    "previousValue": 2,
                    "newValue": 3
                  }
                ],
                "MDI-Z-1-002": [
                  {
                    "key": "MDI-Z-1-002",
                    "message": "Update failed: Max capacity (0) cannot be decreased below current cell occupancy (1)"
                  }
                ],
                "NMI-A-1-001":[
                  {
                    "key":"NMI-A-1-001",
                    "message":"Update failed: Normal accommodation must not have a CNA or working capacity of 0"
                  }
                ],
                "MDI-1-2-008": [
                  {
                    "key": "MDI-1-2-008",
                    "message": "Location not found"
                  }
                ],
                "MDI-Z-1-003": [
                  {
                    "key": "MDI-Z-1-003",
                    "message": "Archived location"
                  }
                ],
                "MDI-B-A-001": [
                  {
                    "key": "MDI-B-A-001",
                    "message": "Max capacity from 2 ==> 3",
                    "type": "maxCapacity",
                    "previousValue": 2,
                    "newValue": 3
                  },
                  {
                    "key": "MDI-B-A-001",
                    "message": "Working capacity from 2 ==> 3",
                    "type": "workingCapacity",
                    "previousValue": 2,
                    "newValue": 3
                  },
                   {
                    "key": "MDI-B-A-001",
                    "message": "Baseline CNA from 2 ==> 3",
                    "type": "CNA",
                    "previousValue": 2,
                    "newValue": 3
                  }
                ]
              }              
              """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-B-A-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-B-A",
            "location.inside.prison.amended" to "MDI-B",
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
                "key": "MDI-Z",
                "capacity": {
                  "maxCapacity": 5,
                  "workingCapacity": 5
                },
                "certification": {
                  "certified": true,
                  "certifiedNormalAccommodation": 5
                },
                "childLocations": [
                  {
                    "key": "MDI-Z-VISIT"
                  },
                  {
                    "key": "MDI-Z-ADJUDICATION"
                  },
                  {
                    "key": "MDI-Z-1",
                    "capacity": {
                      "maxCapacity": 5,
                      "workingCapacity": 5
                    },
                    "certification": {
                      "certified": true,
                      "certifiedNormalAccommodation": 5
                    },
                    "childLocations": [
                      {
                        "key": "MDI-Z-1-01S",
                        "capacity": {
                          "maxCapacity": 0,
                          "workingCapacity": 0
                        },
                        "certification": {
                          "certified": false,
                          "certifiedNormalAccommodation": 0
                        }
                      },
                      {
                        "key": "MDI-Z-1-001",
                        "cellMark": "X001",
                        "inCellSanitation": true,
                        "capacity": {
                          "maxCapacity": 3,
                          "workingCapacity": 3
                        },
                        "certification": {
                          "certified": true,
                          "certifiedNormalAccommodation": 3
                        }
                      },
                      {
                        "key": "MDI-Z-1-002",
                        "cellMark": "X002",
                        "capacity": {
                          "maxCapacity": 2,
                          "workingCapacity": 2
                        },
                        "certification": {
                          "certified": true,
                          "certifiedNormalAccommodation": 2
                        }
                      }
                    ]
                  },
                  {
                    "key": "MDI-Z-2",
                    "capacity": {
                      "maxCapacity": 0,
                      "workingCapacity": 0
                    },
                    "certification": {
                      "certified": false,
                      "certifiedNormalAccommodation": 0
                    }
                  }
                ]
              } 
             """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/bulk/deactivate/permanent")
  @Nested
  inner class BulkPermanentlyDeactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/bulk/deactivate/permanent")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/bulk/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "reason": "Demolished", "locations": [ "${cell1.getKey()}" ] } """)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/bulk/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "reason": "Demolished", "locations": [ "${cell1.getKey()}" ] } """)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/bulk/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(""" { "reason": "Demolished", "locations": [ "${cell1.getKey()}" ] } """)
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `cannot deactivated location with missing reason`() {
        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/permanent")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(""" { "reason": "", "locations": [ "${cell1.getKey()}" ] } """)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(102)
      }

      @Test
      fun `cannot deactivated location which is not already temporarily deactivated`() {
        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/permanent")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(""" { "reason": "Demolished", "locations": [  ] } """)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(102)
      }

      @Test
      fun `cannot deactivated location with missing locations`() {
        assertThat(
          webTestClient.put().uri("/locations/bulk/deactivate/permanent")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(""" { "reason": "Demolished", "locations": [ "${cell1.getKey()}" ] } """)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody<ErrorResponse>()
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(105)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a set of locations`() {
        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()

        landingZ1.temporarilyDeactivate(
          deactivatedReason = DeactivatedReason.MOTHBALLED,
          deactivatedDate = LocalDateTime.now(clock),
          proposedReactivationDate = proposedReactivationDate,
          userOrSystemInContext = EXPECTED_USERNAME,
          linkedTransaction = linkedTransaction,
        )
        repository.save(landingZ1)

        webTestClient.put().uri("/locations/bulk/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              BulkPermanentDeactivationRequest(
                reason = "Demolished",
                locations = listOf(
                  cell1.getKey(),
                  cell2.getKey(),
                  landingZ1.getKey(),
                ),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              [
                {
                  "id": "${cell1.id!!}",
                  "key": "MDI-Z-1-001",
                  "locationType": "CELL",
                  "permanentlyInactive": true,
                  "permanentlyInactiveReason": "Demolished",
                  "status": "ARCHIVED",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "$now"
                },
                 {
                  "id": "${cell2.id!!}",
                  "key": "MDI-Z-1-002",
                  "locationType": "CELL",
                  "permanentlyInactive": true,
                  "permanentlyInactiveReason": "Demolished",
                  "status": "ARCHIVED",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "$now"
                },
                 {
                  "id": "${landingZ1.id!!}",
                  "key": "MDI-Z-1",
                  "locationType": "LANDING",
                  "permanentlyInactive": true,
                  "permanentlyInactiveReason": "Demolished",
                  "status": "ARCHIVED",
                  "active": false,
                  "deactivatedByParent": false,
                  "deactivatedDate": "$now"
                }
              ]
              """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
          )
        }
      }
    }
  }
}

val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomString(length: Int) = List(length) { charPool.random() }.joinToString("")
