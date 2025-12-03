package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Approval Request Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class ApprovalRequestResourceTest : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID
  lateinit var draftWing: ResidentialLocation

  @BeforeEach
  override fun setUp() {
    super.setUp()
    // Create a draft wing in Leeds prison
    draftWing = repository.saveAndFlush(
      CreateEntireWingRequest(
        prisonId = "LEI",
        wingCode = "G",
        numberOfCellsPerSection = 3,
        numberOfLandings = 2,
        numberOfSpurs = 0,
        wingDescription = "Wing G",
      ).toEntity(
        createInDraft = true,
        createdBy = "TEST_USER",
        clock = clock,
        linkedTransaction = linkedTransactionRepository.saveAndFlush(
          LinkedTransaction(
            prisonId = "LEI",
            transactionType = TransactionType.LOCATION_CREATE,
            transactionDetail = "Initial Data Load for Leeds",
            transactionInvokedBy = EXPECTED_USERNAME,
            txStartTime = LocalDateTime.now(clock).minusDays(1),
          ),
        ),
      ),
    )
    approvalRequestId = getApprovalRequestId("LEI-G")
  }

  @DisplayName("GET /certification-approvals")
  @Nested
  inner class GetApprovalRequestsTest {

    private val url = "/certification/request-approvals/prison/LEI"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri(url),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can filter approval requests by prison ID`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$[0].id").isEqualTo(approvalRequestId.toString())
      }

      @Test
      fun `can filter approval requests by status`() {
        webTestClient.get().uri("$url?status=PENDING")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$[0].id").isEqualTo(approvalRequestId.toString())
      }

      @Test
      fun `can filter approval requests by status and no results found`() {
        webTestClient.get().uri("$url?status=APPROVED")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json("[]")
      }

      @Test
      fun `returns empty list when no approval requests match filters`() {
        webTestClient.get().uri("/certification/request-approvals/prison/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json("[]")
      }
    }
  }

  @DisplayName("GET /certification/request-approvals/{id}")
  @Nested
  inner class GetApprovalRequestTest {

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri("/certification/request-approvals/${UUID.randomUUID()}"),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `returns 404 when approval request not found`() {
        val nonExistentId = UUID.randomUUID()
        webTestClient.get().uri("/certification/request-approvals/$nonExistentId")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody().jsonPath("$.userMessage").isEqualTo("Approval not found: Approval request $nonExistentId not found")
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can get approval request by ID`() {
        webTestClient.get().uri("/certification/request-approvals/$approvalRequestId")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "$approvalRequestId",
                "prisonId": "LEI",
                "status": "PENDING",
                "locationKey": "LEI-G",
                "maxCapacityChange": 6,
                "workingCapacityChange": 6,
                "certifiedNormalAccommodationChange": 6,
                 "locations": [
                    {
                      "locationCode": "G",
                      "localName": "Wing G",
                      "pathHierarchy": "G",
                      "level": 1,
                      "certifiedNormalAccommodation": 6,
                      "workingCapacity": 6,
                      "maxCapacity": 6,
                      "locationType": "WING",
                      "accommodationTypes": [
                        "NORMAL_ACCOMMODATION"
                      ],
                      "specialistCellTypes": [
                        "ESCAPE_LIST"
                      ],
                      "usedFor": [
                        "STANDARD_ACCOMMODATION"
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

  private fun getApprovalRequestId(key: String): UUID {
    val aLocation = repository.findOneByKey(key) as ResidentialLocation

    return webTestClient.put().uri("/certification/location/request-approval")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          LocationApprovalRequest(
            locationId = aLocation.id!!,
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
      .expectBody(CertificationApprovalRequestDto::class.java)
      .returnResult().responseBody!!.id
  }
}
