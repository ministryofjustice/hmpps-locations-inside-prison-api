package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Approval Request Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class ApprovalRequestResourceTest : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID

  @BeforeEach
  override fun setUp() {
    super.setUp()
    approvalRequestId = getApprovalRequestId()
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
                "status": "PENDING"
              }
            """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  private fun getApprovalRequestId(): UUID {
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

    return webTestClient.put().uri("/certification/location/request-approval")
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
      .expectBody(CertificationApprovalRequestDto::class.java)
      .returnResult().responseBody!!.id
  }
}
