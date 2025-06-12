package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
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
                "status": "PENDING",
                "locationKey": "LEI-A-1-001",
                "maxCapacityChange": 1,
                "workingCapacityChange": 0,
                "certifiedNormalAccommodationChange": 0,
                 "locations": [
                  {
                    "cellMark": "A-1",
                    "pathHierarchy": "A-1-001",
                    "level": 3,
                    "certifiedNormalAccommodation": 1,
                    "workingCapacity": 1,
                    "maxCapacity": 3,
                    "inCellSanitation": true
                  }
                ]
              }
            """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  private fun getApprovalRequestId(): UUID {
    val aCell = repository.findOneByKey("LEI-A-1-001") as Cell
    prisonerSearchMockServer.stubSearchByLocations("LEI", listOf(aCell.getPathHierarchy()), false)

    webTestClient.put().uri("/locations/${aCell.id}/capacity")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          Capacity(
            workingCapacity = 1,
            maxCapacity = 3,
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
    getDomainEvents(1).let {
      assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
        "location.inside.prison.amended" to aCell.getKey(),
      )
    }
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
