package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationResourceIntTest : CommonDataTestBase() {

  @TestConfiguration
  class FixedClockConfig {
    @Primary
    @Bean
    fun fixedClock(): Clock = clock
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
        webTestClient.get().uri("/locations?size=14&sort=pathHierarchy,asc")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "totalPages": 2,
                "totalElements": 15,
                "first": true,
                "last": false,
                "size": 14,
                "content": [
                  {
                    "prisonId": "NMI",
                    "code": "A",
                    "pathHierarchy": "A",
                    "locationType": "WING",
                    "localName": "WING A",
                    "key": "NMI-A"
                  }, 
                  {
                    "prisonId": "NMI",
                    "code": "1",
                    "pathHierarchy": "A-1",
                    "locationType": "LANDING",
                    "localName": "LANDING A",
                    "key": "NMI-A-1"
                  }, 
                  {
                    "prisonId": "NMI",
                    "code": "001",
                    "pathHierarchy": "A-1-001",
                    "locationType": "CELL",
                    "key": "NMI-A-1-001"
                  }, 
                  {
                    "prisonId": "MDI",
                    "code": "ADJUDICATION",
                    "pathHierarchy": "Z-ADJUDICATION",
                    "locationType": "ADJUDICATION_ROOM",
                    "key": "MDI-Z-ADJUDICATION"
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
                    "code": "A",
                    "pathHierarchy": "B-A",
                    "locationType": "LANDING",
                    "key": "MDI-B-A"
                  },                   {
                    "prisonId": "MDI",
                    "code": "001",
                    "pathHierarchy": "B-A-001",
                    "locationType": "CELL",
                    "key": "MDI-B-A-001"
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
                    "code": "1",
                    "pathHierarchy": "Z-1",
                    "locationType": "LANDING",
                    "key": "MDI-Z-1"
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
                    "code": "01S",
                    "pathHierarchy": "Z-1-01S",
                    "locationType": "STORE",
                    "key": "MDI-Z-1-01S"
                  },
                  {
                    "prisonId": "MDI",
                    "code": "2",
                    "pathHierarchy": "Z-2",
                    "locationType": "LANDING",
                    "key": "MDI-Z-2"
                  }
                ],
                "number": 0,
                "sort": {
                  "empty": false,
                  "sorted": true,
                  "unsorted": false
                },
                "numberOfElements": 14,
                "pageable": {
                  "offset": 0,
                  "sort": {
                    "empty": false,
                    "sorted": true,
                    "unsorted": false
                  },
                  "pageSize": 14,
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
            .expectBody(ErrorResponse::class.java)
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
            .expectBody(ErrorResponse::class.java)
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
            .expectBody(ErrorResponse::class.java)
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
            .expectBody(ErrorResponse::class.java)
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
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, deactivationReasonDescription = "Window smashed", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-1-01S",
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
              "changeHistory": [
                {
                  "attribute": "Deactivation reason",
                  "newValue": "Damage - Window smashed"
                },
                {
                  "attribute": "Status",
                  "oldValue": "Active",
                  "newValue": "Inactive"
                }
              ],
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
                  "code": "ADJUDICATION",
                  "pathHierarchy": "Z-ADJUDICATION",
                  "locationType": "ADJUDICATION_ROOM",
                  "active": false,
                  "deactivatedByParent": true,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": false,
                  "key": "MDI-Z-ADJUDICATION"
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
                      "oldWorkingCapacity": 2,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "localName": "Store Room",
                      "permanentlyInactive": false,
                      "status": "INACTIVE",
                      "active": false,
                      "deactivatedByParent": false,
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "deactivationReasonDescription": "Window smashed",
                      "proposedReactivationDate": "$proposedReactivationDate",
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
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
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
                    "attribute": "Deactivation reason",
                    "newValue": "Other - Not Needed"
                  },
                  {
                    "attribute": "Status",
                    "oldValue": "Active",
                    "newValue": "Inactive"
                  },
                  {
                    "attribute": "Used for",
                    "newValue": "Standard accommodation",
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
                    "attribute": "Deactivation reason",
                    "newValue": "Mothballed"
                  },
                  {
                    "attribute": "Status",
                    "oldValue": "Active",
                    "newValue": "Inactive"
                  },
                  {
                    "attribute": "Used for",
                    "newValue": "Standard accommodation",
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

        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, deactivationReasonDescription = "Spiders", planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
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
                      "attribute": "Deactivation reason",
                      "oldValue": "Damage",
                      "newValue": "Mothballed - Spiders"
                    },
                    {
                      "attribute": "Deactivation reason",
                      "newValue": "Damage"
                    },
                    {
                      "attribute": "Status",
                      "oldValue": "Active",
                      "newValue": "Inactive"
                    },
                    {
                      "attribute": "Used for",
                      "newValue": "Standard accommodation",
                      "amendedBy": "A_TEST_USER"
                    }
                  ]
            }
          """,
            false,
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

        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = "Poor state", planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
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
                  "attribute": "Deactivation reason",
                  "oldValue": "Damage - Water damage",
                  "newValue": "Other - Poor state"
                },
                {
                  "attribute": "Deactivation reason",
                  "newValue": "Damage - Water damage"
                },
                {
                  "attribute": "Status",
                  "oldValue": "Active",
                  "newValue": "Inactive"
                },
                {
                  "attribute": "Used for",
                  "newValue": "Standard accommodation",
                  "amendedBy": "A_TEST_USER"
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
      fun `can cascade reactivated locations`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-1-01S",
          )
        }

        webTestClient.put().uri("/locations/${wingZ.id}/reactivate?cascade-reactivation=true")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(12).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1-002",
            "location.inside.prison.reactivated" to "MDI-Z-1-01S",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1-01S",
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
                "capacityOfCertifiedCell": 4
              },
              "changeHistory": [
                {
                  "attribute": "Status",
                  "oldValue": "Inactive",
                  "newValue": "Active"
                },
                {
                  "attribute": "Deactivation reason",
                  "newValue": "Mothballed"
                },
                {
                  "attribute": "Status",
                  "oldValue": "Active",
                  "newValue": "Inactive"
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
                      "key": "MDI-Z-1-001",
                      "changeHistory": [
                        {
                          "attribute": "Status",
                          "oldValue": "Inactive",
                          "newValue": "Active"
                        },
                        {
                          "attribute": "Deactivation reason",
                          "newValue": "Mothballed"
                        },
                        {
                          "attribute": "Used for",
                          "newValue": "Standard accommodation",
                          "amendedBy": "A_TEST_USER"
                        }
                      ]
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
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-1-01S",
          )
        }

        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.amended" to "MDI-Z-1-001",
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
                      "key": "MDI-Z-1-001",
                      "changeHistory": [
                        {
                          "attribute": "Status",
                          "oldValue": "Inactive",
                          "newValue": "Active"
                        },
                        {
                          "attribute": "Deactivation reason",
                          "newValue": "Damage"
                        },
                        {
                          "attribute": "Status",
                          "oldValue": "Active",
                          "newValue": "Inactive"
                        },
                        {
                          "attribute": "Used for",
                          "newValue": "Standard accommodation",
                          "amendedBy": "A_TEST_USER"
                        }
                      ]
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
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
                      "active": false,
                      "deactivatedReason": "MOTHBALLED",
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
            false,
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
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(cell1.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER)))))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
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
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(cell1.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.OTHER, deactivationReasonDescription = " ")))))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
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
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(cell1.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody(ErrorResponse::class.java)
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
            .bodyValue(jsonString(DeactivateLocationsRequest(mapOf(wingZ.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody(ErrorResponse::class.java)
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
                mapOf(
                  cell1.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, deactivationReasonDescription = "Window smashed", proposedReactivationDate = proposedReactivationDate),
                  cell2.id!! to TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.REFURBISHMENT, deactivationReasonDescription = "Fire", planetFmReference = "XXX122"),
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
                    "capacityOfCertifiedCell": 2
                  },
                  "accommodationTypes": [
                    "NORMAL_ACCOMMODATION"
                  ],
                  "usedFor": [
                    "STANDARD_ACCOMMODATION"
                  ],
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
                    "capacityOfCertifiedCell": 2
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
                  "status": "INACTIVE",
                  "active": false,
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
            false,
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

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-1-01S",
          )
        }

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
                "maxCapacity": 5,
                "workingCapacity": 5
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
                      "key": "MDI-Z-1-001",
                      "capacity": {
                        "maxCapacity": 3,
                        "workingCapacity": 3
                      }
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 2
                      }
                    },
                    {
                      "prisonId": "MDI",
                      "code": "01S",
                      "pathHierarchy": "Z-1-01S",
                      "locationType": "STORE",
                      "active": false,
                      "deactivatedReason": "DAMAGED",
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
            false,
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

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to "MDI-Z-1-01S",
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z-1",
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

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-1-01S",
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

        getDomainEvents(9).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to store.getKey(),
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
            false,
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
                  cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3, capacityOfCertifiedCell = 3),
                  cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 0, workingCapacity = 0),
                  cell1N.getKey() to CellCapacityUpdateDetail(maxCapacity = 4, workingCapacity = 1),
                  "MDI-1-2-008" to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3),
                  archivedCell.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3),
                  inactiveCellB3001.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 3),
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
                "NMI-A-1-001": [
                  {
                    "key": "NMI-A-1-001",
                    "message": "Max capacity from 2 ==> 4",
                    "type": "maxCapacity",
                    "previousValue": 2,
                    "newValue": 4
                  },
                  {
                    "key": "NMI-A-1-001",
                    "message": "Working capacity from 2 ==> 1",
                    "type": "workingCapacity",
                    "previousValue": 2,
                    "newValue": 1
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
                  }
                ]
              }              
              """,
            false,
          )

        getDomainEvents(9).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "NMI-A-1-001",
            "location.inside.prison.amended" to "MDI-B-A-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.amended" to "NMI-A-1",
            "location.inside.prison.amended" to "NMI-A",
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
                  "capacityOfCertifiedCell": 5
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
                      "capacityOfCertifiedCell": 5
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
                          "capacityOfCertifiedCell": 0
                        }
                      },
                      {
                        "key": "MDI-Z-1-001",
                        "capacity": {
                          "maxCapacity": 3,
                          "workingCapacity": 3
                        },
                        "certification": {
                          "certified": true,
                          "capacityOfCertifiedCell": 3
                        }
                      },
                      {
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
            .expectBody(ErrorResponse::class.java)
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
            .expectBody(ErrorResponse::class.java)
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
            .expectBody(ErrorResponse::class.java)
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
          clock = clock,
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
            false,
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
