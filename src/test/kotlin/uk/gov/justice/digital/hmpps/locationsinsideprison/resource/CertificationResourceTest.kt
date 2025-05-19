package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.util.UUID

@DisplayName("Certification Resource")
class CertificationResourceTest : CommonDataTestBase() {

  @Autowired
  lateinit var certificationApprovalRequestRepository: CertificationApprovalRequestRepository

  @Nested
  @DisplayName("Request Approval Tests")
  inner class RequestApprovalTest {

    @Nested
    @DisplayName("Security")
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      @WithMockAuthUser
      fun `access forbidden when no role`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_WRONG"])
      fun `access forbidden with wrong role`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `access client error bad data`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue("{}")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `location not found returns 404`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue(
            """
            {
              "locationId": "${UUID.randomUUID()}"
            }
            """.trimIndent(),
          )
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `location not in DRAFT or LOCKED status returns 400`() {
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue(
            """
            {
              "locationId": "${cell1.id}"
            }
            """.trimIndent(),
          )
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `can request approval for a location in DRAFT status`() {
        // Create a location in DRAFT status
        val draftLocationId = webTestClient.post()
          .uri("/locations/residential")
          .bodyValue(
            CreateResidentialLocationRequest(
              prisonId = "LEI",
              code = "DRAFT1",
              locationType = ResidentialLocationType.CELL,
              parentId = leedsWing.id,
              accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
            ),
          )
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isCreated
          .expectBody()
          .jsonPath("$.id").isNotEmpty
          .jsonPath("$.status").isEqualTo("DRAFT")
          .returnResult()
          .responseBody?.let { objectMapper.readValue(it, CertificationApprovalRequestDto::class.java)?.id }
          ?: UUID.randomUUID()

        // Request approval
        webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue(
            LocationApprovalRequest(
              locationId = draftLocationId,
            ),
          )
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.locationId").isEqualTo(draftLocationId.toString())
          .jsonPath("$.status").isEqualTo("PENDING")
          .jsonPath("$.requestedBy").isEqualTo(EXPECTED_USERNAME)

        // Verify the database
        val approvalRequests = certificationApprovalRequestRepository.findByLocationOrderByRequestedDateDesc(
          repository.findById(draftLocationId).get(),
        )
        assertThat(approvalRequests).isNotEmpty
        assertThat(approvalRequests[0].status).isEqualTo(ApprovalRequestStatus.PENDING)
        assertThat(approvalRequests[0].location.id).isEqualTo(draftLocationId)
      }
    }
  }

  @Nested
  @DisplayName("Approve Certification Request Tests")
  inner class ApproveCertificationRequestTest {

    @Nested
    @DisplayName("Security")
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put()
          .uri("/certification/location/approve")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      @WithMockAuthUser
      fun `access forbidden when no role`() {
        webTestClient.put()
          .uri("/certification/location/approve")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_WRONG"])
      fun `access forbidden with wrong role`() {
        webTestClient.put()
          .uri("/certification/location/approve")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `access client error bad data`() {
        webTestClient.put()
          .uri("/certification/location/approve")
          .bodyValue("{}")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `approval request not found returns 404`() {
        webTestClient.put()
          .uri("/certification/location/approve")
          .bodyValue(
            """
            {
              "approvalRequestReference": "${UUID.randomUUID()}"
            }
            """.trimIndent(),
          )
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `can approve a certification request`() {
        // Create a location in DRAFT status
        val draftLocation = webTestClient.post()
          .uri("/locations/residential")
          .bodyValue(
            CreateResidentialLocationRequest(
              prisonId = "LEI",
              code = "DRAFT2",
              locationType = ResidentialLocationType.CELL,
              parentId = leedsWing.id,
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isCreated
          .returnResult()
          .responseBody

        // Request approval
        val approvalRequest = webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue(
            LocationApprovalRequest(
              locationId = draftLocation.id,
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isOk
          .returnResult()
          .responseBody

        // Approve the request
        val result = webTestClient.put()
          .uri("/certification/location/approve")
          .bodyValue(
            ApproveCertificationRequestDto(
              approvalRequestReference = approvalRequest.id,
              comments = "Approved",
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isOk
          .returnResult()
          .responseBody

        // Verify the result
        assertThat(result.id).isEqualTo(approvalRequest.id)
        assertThat(result.status).isEqualTo(ApprovalRequestStatus.APPROVED)
        assertThat(result.approvedOrRejectedBy).isEqualTo(EXPECTED_USERNAME)
        assertThat(result.comments).isEqualTo("Approved")

        // Verify the database
        val updatedApprovalRequest = certificationApprovalRequestRepository.findById(result.id).get()
        assertThat(updatedApprovalRequest.status).isEqualTo(ApprovalRequestStatus.APPROVED)
        assertThat(updatedApprovalRequest.approvedOrRejectedBy).isEqualTo(EXPECTED_USERNAME)
        assertThat(updatedApprovalRequest.comments).isEqualTo("Approved")

        // Verify the location status is now INACTIVE
        val updatedLocation = repository.findById(draftLocation.id).get()
        assertThat(updatedLocation.status).isEqualTo(LocationStatus.INACTIVE)
      }
    }
  }

  @Nested
  @DisplayName("Reject Certification Request Tests")
  inner class RejectCertificationRequestTest {

    @Nested
    @DisplayName("Security")
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put()
          .uri("/certification/location/reject")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      @WithMockAuthUser
      fun `access forbidden when no role`() {
        webTestClient.put()
          .uri("/certification/location/reject")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_WRONG"])
      fun `access forbidden with wrong role`() {
        webTestClient.put()
          .uri("/certification/location/reject")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `access client error bad data`() {
        webTestClient.put()
          .uri("/certification/location/reject")
          .bodyValue("{}")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `approval request not found returns 404`() {
        webTestClient.put()
          .uri("/certification/location/reject")
          .bodyValue(
            """
            {
              "approvalRequestReference": "${UUID.randomUUID()}"
            }
            """.trimIndent(),
          )
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

      @Test
      @WithMockAuthUser(authorities = ["ROLE_LOCATION_CERTIFICATION"])
      fun `can reject a certification request`() {
        // Create a location in DRAFT status
        val draftLocation = webTestClient.post()
          .uri("/locations/residential")
          .bodyValue(
            CreateResidentialLocationRequest(
              prisonId = "LEI",
              code = "DRAFT3",
              locationType = ResidentialLocationType.CELL,
              parentId = leedsWing.id,
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isCreated
          .returnResult()
          .responseBody

        // Request approval
        val approvalRequest = webTestClient.put()
          .uri("/certification/location/request-approval")
          .bodyValue(
            LocationApprovalRequest(
              locationId = draftLocation.id,
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isOk
          .returnResult()
          .responseBody

        // Reject the request
        val result = webTestClient.put()
          .uri("/certification/location/reject")
          .bodyValue(
            RejectCertificationRequestDto(
              approvalRequestReference = approvalRequest.id,
              comments = "Rejected",
            ),
          )
          .headers { it.authToken() }
          .exchange()
          .expectStatus().isOk
          .returnResult()
          .responseBody

        // Verify the result
        assertThat(result.id).isEqualTo(approvalRequest.id)
        assertThat(result.status).isEqualTo(ApprovalRequestStatus.REJECTED)
        assertThat(result.approvedOrRejectedBy).isEqualTo(EXPECTED_USERNAME)
        assertThat(result.comments).isEqualTo("Rejected")

        // Verify the database
        val updatedApprovalRequest = certificationApprovalRequestRepository.findById(result.id).get()
        assertThat(updatedApprovalRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)
        assertThat(updatedApprovalRequest.approvedOrRejectedBy).isEqualTo(EXPECTED_USERNAME)
        assertThat(updatedApprovalRequest.comments).isEqualTo("Rejected")

        // Verify the location status is still DRAFT
        val updatedLocation = repository.findById(draftLocation.id).get()
        assertThat(updatedLocation.status).isEqualTo(LocationStatus.DRAFT)
      }
    }
  }

  private fun org.springframework.web.reactive.function.client.WebHeaders.authToken() {
    this.add("Authorization", "Bearer token")
    this.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)
  }
}
