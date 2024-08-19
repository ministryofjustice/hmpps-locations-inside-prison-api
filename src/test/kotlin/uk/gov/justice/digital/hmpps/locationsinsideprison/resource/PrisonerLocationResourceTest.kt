package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class PrisonerLocationResourceTest : CommonDataTestBase() {

  @DisplayName("GET /prisoner-locations/id/{id}")
  @Nested
  inner class PrisonerLocationsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prisoner-locations/id/${cell1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-locations/id/${cell1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-locations/id/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve a map of prisoners in cells within this location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell1.getPathHierarchy()), true)
        webTestClient.get().uri("/prisoner-locations/id/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("VIEW_PRISONER_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            [
              {
                "cellLocation": "Z-1-001",
                "prisoners": [
                  {
                    "prisonerNumber": "A0000AA",
                    "prisonId": "MDI",
                    "prisonName": "MDI",
                    "cellLocation": "Z-1-001",
                    "firstName": "Firstname-0",
                    "lastName": "Surname-0",
                    "gender": "MALE"
                  },
                  {
                    "prisonerNumber": "A0001AA",
                    "prisonId": "MDI",
                    "prisonName": "MDI",
                    "cellLocation": "Z-1-001",
                    "firstName": "Firstname-1",
                    "lastName": "Surname-1",
                    "gender": "MALE"
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

  @Nested
  inner class PrisonerPrisonIdTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prisoner-locations/prison/${wingZ.prisonId}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-locations/prison/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-locations/prison/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve a map of prisoners in cells within this prisonId`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell1N.prisonId,
          listOf(cell1N.getPathHierarchy(), cell1N.getPathHierarchy()),
          true,
        )

        webTestClient.get().uri("/prisoner-locations/prison/${cell1N.prisonId}")
          .headers(setAuthorisation(roles = listOf("VIEW_PRISONER_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            [
              {
                "cellLocation": "A-1-001",
                "prisoners": [
                  {
                    "prisonerNumber": "A0000AA",
                    "prisonId": "NMI",
                    "prisonName": "NMI",
                    "cellLocation": "A-1-001",
                    "firstName": "Firstname-0",
                    "lastName": "Surname-0",
                    "gender": "MALE"
                  },
                  {
                    "prisonerNumber": "A0001AA",
                    "prisonId": "NMI",
                    "prisonName": "NMI",
                    "cellLocation": "A-1-001",
                    "firstName": "Firstname-1",
                    "lastName": "Surname-1",
                    "gender": "MALE"
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

  @Nested
  inner class PrisonerLocationKeyTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prisoner-locations/key/${wingZ.getKey()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-locations/prison/${wingZ.getKey()}")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prisoner-locations/prison/${wingZ.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve a map of prisoners in cells within this code`() {
        prisonerSearchMockServer.stubSearchByLocations(
          cell1N.prisonId,
          listOf(cell1N.getPathHierarchy(), cell1N.getPathHierarchy()),
          true,
        )

        webTestClient.get().uri("/prisoner-locations/key/${cell1N.getKey()}")
          .headers(setAuthorisation(roles = listOf("VIEW_PRISONER_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            [
              {
                "cellLocation": "A-1-001",
                "prisoners": [
                  {
                    "prisonerNumber": "A0000AA",
                    "prisonId": "NMI",
                    "prisonName": "NMI",
                    "cellLocation": "A-1-001",
                    "firstName": "Firstname-0",
                    "lastName": "Surname-0",
                    "gender": "MALE"
                  },
                  {
                    "prisonerNumber": "A0001AA",
                    "prisonId": "NMI",
                    "prisonName": "NMI",
                    "cellLocation": "A-1-001",
                    "firstName": "Firstname-1",
                    "lastName": "Surname-1",
                    "gender": "MALE"
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
}
