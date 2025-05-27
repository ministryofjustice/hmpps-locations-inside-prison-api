package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Certification Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationResourceTest : CommonDataTestBase() {

  @DisplayName("PUT /certification/location/request-approval")
  @Nested
  inner class RequestApprovalTest {

    private val url = "/certification/location/request-approval"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put().uri(url).bodyValue(
          jsonString(
            LocationApprovalRequest(
              locationId = UUID.randomUUID(),
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `cannot request approval for a location which is not DRAFT or has any pending changes`() {
        val aCell = leedsWing.cellLocations().find { it.getKey() == "LEI-A-1-001" } ?: throw RuntimeException("Cell not found")

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              LocationApprovalRequest(
                locationId = aCell.id!!,
              ),
            ),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can request approval for a location that has pending changes`() {
        val aCell = repository.findOneByKey("LEI-A-1-001") as Cell
        aCell.setCapacity(
          maxCapacity = 3,
          workingCapacity = 2,
          userOrSystemInContext = EXPECTED_USERNAME,
          amendedDate = LocalDateTime.now(
            clock,
          ),
          linkedTransaction = linkedTransaction,
        )
        repository.saveAndFlush(aCell)

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              LocationApprovalRequest(
                locationId = aCell.id!!,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationId": "${aCell.id}",
              "locationKey": "${aCell.getKey()}",
              "status": "PENDING"
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can request approval for a location that is a draft location`() {
        // Create a new wing in Leeds prison
        val mWing = repository.saveAndFlush(
          CreateEntireWingRequest(
            prisonId = "LEI",
            wingCode = "M",
            numberOfCellsPerSection = 3,
            numberOfLandings = 2,
            numberOfSpurs = 0,
            defaultCellCapacity = 1,
            wingDescription = "Wing M",
          ).toEntity(
            createInDraft = true,
            createdBy = EXPECTED_USERNAME,
            clock = clock,
            linkedTransaction = linkedTransactionRepository.saveAndFlush(
              LinkedTransaction(
                prisonId = "LEI",
                transactionType = TransactionType.LOCATION_CREATE,
                transactionDetail = "New Wing M in Leeds",
                transactionInvokedBy = EXPECTED_USERNAME,
                txStartTime = LocalDateTime.now(clock).minusDays(1),
              ),
            ),
          ),
        )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              LocationApprovalRequest(
                locationId = mWing.id!!,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationId": "${mWing.id}",
              "locationKey": "${mWing.getKey()}",
              "status": "PENDING"
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }
}
