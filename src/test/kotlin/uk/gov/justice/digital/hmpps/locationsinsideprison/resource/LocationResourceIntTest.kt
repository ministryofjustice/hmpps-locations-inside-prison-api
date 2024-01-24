package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.test.context.support.WithMockUser
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location as LocationJPA

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
  lateinit var location: LocationJPA
  lateinit var landing1: LocationJPA
  lateinit var wing: LocationJPA

  @BeforeEach
  fun setUp() {
    repository.deleteAll()

    wing = repository.save(buildLocation(pathHierarchy = "Z", locationType = LocationType.WING))
    val landing = repository.save(buildLocation(pathHierarchy = "Z-1", locationType = LocationType.LANDING))
    val cell1 = repository.save(buildLocation(pathHierarchy = "Z-1-001"))
    val cell2 = repository.save(buildLocation(pathHierarchy = "Z-1-002"))
    wing.addChildLocation(landing.addChildLocation(cell1).addChildLocation(cell2))
    repository.save(wing)
    location = cell1
    landing1 = landing
  }

  private fun buildLocation(
    prisonId: String = "MDI",
    pathHierarchy: String,
    locationType: LocationType = LocationType.CELL,
  ) = LocationJPA(
    prisonId = prisonId,
    code = pathHierarchy.split("-").last(),
    pathHierarchy = pathHierarchy,
    locationType = locationType,
    updatedBy = EXPECTED_USERNAME,
    whenCreated = LocalDateTime.now(clock),
    whenUpdated = LocalDateTime.now(clock),
  )

  @DisplayName("GET /locations/{id}")
  @Nested
  inner class ViewLocationTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/${location.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/${location.id}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/${location.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve details of a location`() {
        webTestClient.get().uri("/locations/${wing.id}?includeChildren=true")
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
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "active": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "active": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "active": true,
                      "key": "MDI-Z-1-002"
                    }
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
                "totalElements": 4,
                "first": true,
                "last": true,
                "size": 10,
                "content": [
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
                  }

                ],
                "number": 0,
                "sort": {
                  "empty": false,
                  "sorted": true,
                  "unsorted": false
                },
                "numberOfElements": 4,
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

  @DisplayName("POST /locations")
  @Nested
  inner class CreateLocationTest {
    var createLocationRequest = CreateLocationRequest(
      prisonId = "MDI",
      code = "004",
      locationType = LocationType.CELL,
      description = "A New Cell (004)",
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "This is a new cell",
      orderWithinParentLocation = 4,
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations")
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
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createLocationRequest.copy(parentId = landing1.id)))
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
              "active": true,
              "key": "MDI-Z-1-004",
              "comments": "This is a new cell",
              "description": "A New Cell (004)",
              "orderWithinParentLocation": 4
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("PATCH /locations/{id}")
  @Nested
  inner class PatchLocationTest {
    val patchLocationRequest = PatchLocationRequest(
      code = "2",
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
          .bodyValue(jsonString(patchLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(patchLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(patchLocationRequest))
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
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update details of a location`() {
        webTestClient.patch().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(patchLocationRequest))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "2",
              "pathHierarchy": "Z-2",
              "locationType": "LANDING",
              "active": true,
              "key": "MDI-Z-2"
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
        webTestClient.put().uri("/locations/${location.id}/deactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${location.id}/deactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${location.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${location.id}/deactivate")
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
        webTestClient.put().uri("/locations/${location.id}/deactivate")
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
        webTestClient.put().uri("/locations/${location.id}/deactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
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
              "active": false,
              "key": "MDI-Z-1-001",
              "deactivatedReason": "DAMAGED",
              "deactivatedDate": "${LocalDate.now(clock)}"
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
        webTestClient.put().uri("/locations/${location.id}/reactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${location.id}/reactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${location.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${location.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a location`() {
        webTestClient.put().uri("/locations/${location.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(DeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
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
              "reactivatedDate": "${LocalDate.now(clock)}"
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("DELETE /locations/{id}")
  @Nested
  inner class DeleteLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.delete().uri("/locations")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.delete().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.delete().uri("/locations/${landing1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is2xxSuccessful
          .expectBody()
      }
    }
  }
}
