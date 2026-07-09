package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UnArchiveLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PermanentDeactivationApprovalRequestDto
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
@DisplayName("PUT /locations/{id}/unarchive")
class UnarchiveLocationResourceTest : CommonDataTestBase() {

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .headers(setAuthorisation(roles = listOf()))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with right role, wrong scope`() {
      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .headers(setAuthorisation(roles = listOf("ROLE_UNARCHIVE_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class Validation {
    @Test
    fun `cannot un-archive a location that is not archived`() {
      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .headers(setAuthorisation(roles = listOf("ROLE_UNARCHIVE_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.LocationCannotBeUnarchived.errorCode)
    }
  }

  @Nested
  inner class HappyPath {
    @Test
    fun `un-archives a location in a prison without certification approval, restoring it to temporarily inactive`() {
      prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy()), false)

      // archive cell1 - MDI does not require certification approval
      webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Archived in error")))
        .exchange()
        .expectStatus().isOk

      purgeDomainEvents()

      webTestClient.put().uri("/locations/${cell1.id}/unarchive")
        .headers(setAuthorisation(roles = listOf("ROLE_UNARCHIVE_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, reason = "Archived in error")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "key": "${cell1.getKey()}",
            "active": false,
            "permanentlyInactive": false,
            "deactivatedReason": "MOTHBALLED",
            "certifiedCell": true,
            "capacity": {
              "maxCapacity": 2,
              "certifiedNormalAccommodation": 2
            }
          }
          """,
          JsonCompareMode.LENIENT,
        )

      getDomainEvents(3).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to "MDI-Z-1",
          "location.inside.prison.amended" to "MDI-Z",
          "location.inside.prison.reactivated" to cell1.getKey(),
        )
      }
    }

    @Test
    fun `un-archives a location in a prison with certification approval, regenerating the certificate via an auto-approved request`() {
      val cell = leedsWing.findAllLeafLocations().first() as Cell

      archiveViaApproval(cell)

      // the archived cell has been removed from the current certificate
      assertCurrentCertificateContainsCell(cell, expected = false)

      purgeDomainEvents()

      webTestClient.put().uri("/locations/${cell.id}/unarchive")
        .headers(setAuthorisation(roles = listOf("ROLE_UNARCHIVE_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(UnArchiveLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, reason = "Archived in error")))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
          {
            "key": "${cell.getKey()}",
            "active": false,
            "permanentlyInactive": false,
            "deactivatedReason": "MOTHBALLED",
            "certifiedCell": true,
            "capacity": {
              "maxCapacity": 2,
              "certifiedNormalAccommodation": 1
            }
          }
          """,
          JsonCompareMode.LENIENT,
        )

      // the certificate was regenerated and now shows the restored (inactive) cell again
      assertCurrentCertificateContainsCell(cell, expected = true)

      // the restore was recorded as an auto-approved un-archive request for audit
      val unArchiveRequests = certificationApprovalRequestRepository.findAll()
        .filter { it.getApprovalType() == ApprovalType.UN_ARCHIVE }
      assertThat(unArchiveRequests).hasSize(1)
      assertThat(unArchiveRequests.first().status).isEqualTo(ApprovalRequestStatus.APPROVED)
      assertThat(unArchiveRequests.first().prisonId).isEqualTo(leedsWing.prisonId)
    }
  }

  private fun archiveViaApproval(cell: Cell) {
    // the cell must be temporarily deactivated before permanent deactivation approval can be requested
    prisonerSearchMockServer.stubSearchByLocations(cell.prisonId, listOf(cell.getPathHierarchy()), false)
    webTestClient.put().uri("/locations/${cell.id}/deactivate/temporary")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
      .exchange()
      .expectStatus().isOk

    val pendingApproval = webTestClient.put().uri("/certification/location/permanent-deactivation-request-approval")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(PermanentDeactivationApprovalRequestDto(locationId = cell.id!!, reason = "Cell demolished")))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingApproval.id)))
      .exchange()
      .expectStatus().isOk
  }

  private fun assertCurrentCertificateContainsCell(cell: ResidentialLocation, expected: Boolean) {
    webTestClient.get().uri("/cell-certificates/prison/${cell.prisonId}/current")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$..pathHierarchy").value<List<String>> { paths ->
        if (expected) {
          assertThat(paths).contains(cell.getPathHierarchy())
        } else {
          assertThat(paths).doesNotContain(cell.getPathHierarchy())
        }
      }
  }
}
