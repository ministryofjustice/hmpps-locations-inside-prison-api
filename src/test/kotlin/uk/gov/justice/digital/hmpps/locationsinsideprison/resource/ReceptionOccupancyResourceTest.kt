package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.wiremock.createPrisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildVirtualResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class ReceptionOccupancyResourceTest : CommonDataTestBase() {

  // The search always asks for the whole reception set, sorted - never CSWAP
  private val receptionCodes = listOf("COURT", "RECP", "TAP")

  private fun saveReception(capacity: Capacity): VirtualResidentialLocation = repository.save(
    buildVirtualResidentialLocation(pathHierarchy = "RECP", localName = "Reception", capacity = capacity),
  )

  @DisplayName("GET /location-occupancy/reception/{prisonId}")
  @Nested
  inner class ReceptionOccupancyTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/location-occupancy/reception/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/location-occupancy/reception/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/location-occupancy/reception/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `counts only prisoners in RECP, and lists everyone in reception`() {
        val reception = saveReception(Capacity(maxCapacity = 10, workingCapacity = 0))
        prisonerSearchMockServer.stubSearchByLocations(
          prisonId = "MDI",
          locations = receptionCodes,
          prisoners = listOf(
            createPrisoner(prisonId = "MDI", cellLocation = "TAP", prisonerCount = 1),
            createPrisoner(prisonId = "MDI", cellLocation = "RECP", prisonerCount = 2),
            createPrisoner(prisonId = "MDI", cellLocation = "RECP", prisonerCount = 3, inOutStatus = "OUT", status = "ACTIVE OUT"),
            createPrisoner(prisonId = "MDI", cellLocation = "COURT", prisonerCount = 4),
            createPrisoner(prisonId = "MDI", cellLocation = "COURT", prisonerCount = 5, inOutStatus = "OUT", status = "ACTIVE OUT"),
            createPrisoner(prisonId = "MDI", cellLocation = "RECP", prisonerCount = 6),
          ),
        )

        webTestClient.get().uri("/location-occupancy/reception/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "id": "${reception.id}",
              "prisonId": "MDI",
              "pathHierarchy": "RECP",
              "key": "MDI-RECP",
              "maxCapacity": 10,
              "workingCapacity": 0,
              "noOfOccupants": 2,
              "hasSpace": true
            }
            """,
            JsonCompareMode.LENIENT,
          )
          // IN only, across RECP, COURT and TAP, in cell location order
          .jsonPath("$.prisoners[*].prisonerNumber").isEqualTo(listOf("A0004AA", "A0002AA", "A0006AA", "A0001AA"))
          .jsonPath("$.prisoners[*].cellLocation").isEqualTo(listOf("COURT", "RECP", "RECP", "TAP"))
      }

      @Test
      fun `reports no space when RECP is at its working capacity`() {
        saveReception(Capacity(maxCapacity = 10, workingCapacity = 2))
        prisonerSearchMockServer.stubSearchByLocations(
          prisonId = "MDI",
          locations = receptionCodes,
          prisoners = listOf(
            createPrisoner(prisonId = "MDI", cellLocation = "RECP", prisonerCount = 1),
            createPrisoner(prisonId = "MDI", cellLocation = "RECP", prisonerCount = 2),
          ),
        )

        webTestClient.get().uri("/location-occupancy/reception/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.noOfOccupants").isEqualTo(2)
          .jsonPath("$.hasSpace").isEqualTo(false)
      }

      @Test
      fun `reports no capacity and no space for a prison without a reception`() {
        prisonerSearchMockServer.stubSearchByLocations(prisonId = "BXI", locations = receptionCodes)

        webTestClient.get().uri("/location-occupancy/reception/BXI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.id").doesNotExist()
          .jsonPath("$.key").isEqualTo("BXI-RECP")
          .jsonPath("$.maxCapacity").isEqualTo(0)
          .jsonPath("$.noOfOccupants").isEqualTo(0)
          .jsonPath("$.hasSpace").isEqualTo(false)
          .jsonPath("$.prisoners.length()").isEqualTo(0)
      }
    }
  }
}
