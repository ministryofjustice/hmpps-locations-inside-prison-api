package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellMarkChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellSanitationChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime

@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationApprovalResourceTest : CommonDataTestBase() {

  @DisplayName("PUT /locations/{id}/deactivate")
  @Nested
  inner class DeactivateLocationTest {

    @Test
    fun `can deactivate a location and request approval`() {
      val now = LocalDateTime.now(clock)
      val proposedReactivationDate = now.plusMonths(1).toLocalDate()
      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        leedsWing.findAllLeafLocations().map { it.getPathHierarchy() },
        false,
      )

      webTestClient.put().uri("/locations/${leedsWing.id}/deactivate/temporary")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            TemporaryDeactivationLocationRequest(
              requiresApproval = true,
              reasonForChange = "The cell as been flooded",
              deactivationReason = DeactivatedReason.MOTHBALLED,
              proposedReactivationDate = proposedReactivationDate,
              planetFmReference = "222333",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val deactivatedLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(deactivatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(deactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(deactivatedLocation.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(deactivatedLocation.pendingApprovalRequestId).isNotNull

      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

      getDomainEvents(9).let { messages ->
        val results = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *results.map { "location.inside.prison.deactivated" to it }.toTypedArray(),
        )
      }

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.DEACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(leedsWing.id)
      assertThat(pendingApproval.prisonId).isEqualTo(leedsWing.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(leedsWing.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(-6)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.reasonForChange).isEqualTo("The cell as been flooded")
      assertThat(pendingApproval.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(pendingApproval.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(pendingApproval.locations).hasSize(1)

      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      webTestClient.get().uri("/cell-certificates/${approvedRequest.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(approvedRequest.certificateId)
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(12)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(0)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(6)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(0)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(0)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].workingCapacity").isEqualTo(0)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].maxCapacity").isEqualTo(2)

      val approvedDeactivatedLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedDeactivatedLocation.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(approvedDeactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(approvedDeactivatedLocation.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(approvedDeactivatedLocation.pendingApprovalRequestId).isNull()
    }
  }

  @DisplayName("PUT /locations/residential/{id}/cell-mark-change")
  @Nested
  inner class CellMarkChangeTest {

    @Test
    fun `can change a cell mark on a location and request approval`() {
      val cellToUpdate = leedsWing.findAllLeafLocations().first() as Cell
      val pendingCell = webTestClient.put().uri("/locations/residential/${cellToUpdate.id}/cell-mark-change")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CellMarkChangeRequest(
              reasonForChange = "The door number is wrong",
              cellMark = "CM-001",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(pendingCell.status).isEqualTo(DerivedLocationStatus.LOCKED_ACTIVE)
      assertThat(pendingCell.cellMark).isEqualTo(cellToUpdate.cellMark)
      assertThat(pendingCell.pendingChanges?.cellMark).isEqualTo("CM-001")
      assertThat(pendingCell.pendingApprovalRequestId).isNotNull

      val pendingApprovalRequestId = pendingCell.pendingApprovalRequestId!!

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.CELL_MARK)
      assertThat(pendingApproval.locationId).isEqualTo(cellToUpdate.id)
      assertThat(pendingApproval.prisonId).isEqualTo(cellToUpdate.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(cellToUpdate.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.cellMark).isEqualTo("CM-001")
      assertThat(pendingApproval.currentCellMark).isEqualTo("A-1")
      assertThat(pendingApproval.reasonForChange).isEqualTo("The door number is wrong")
      assertThat(pendingApproval.locations).isNotEmpty
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].cellMark).isEqualTo("CM-001")

      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      webTestClient.get().uri("/cell-certificates/${approvedRequest.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(approvedRequest.certificateId)
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(12)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(6)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(6)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].cellMark").isEqualTo("CM-001")

      val approvedLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(approvedLocation.cellMark).isEqualTo("CM-001")
      assertThat(approvedLocation.pendingChanges).isNull()
      assertThat(approvedLocation.pendingApprovalRequestId).isNull()
    }
  }

  @DisplayName("PUT /locations/residential/{id}/cell-sanitation-change")
  @Nested
  inner class CellSanitationChangeTest {

    @Test
    fun `can change a cell mark on a location and request approval`() {
      val cellToUpdate = leedsWing.findAllLeafLocations().first() as Cell
      val pendingCell = webTestClient.put().uri("/locations/residential/${cellToUpdate.id}/cell-sanitation-change")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CellSanitationChangeRequest(
              reasonForChange = "The toilet is old",
              inCellSanitation = false,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(pendingCell.status).isEqualTo(DerivedLocationStatus.LOCKED_ACTIVE)
      assertThat(pendingCell.inCellSanitation).isEqualTo(cellToUpdate.inCellSanitation)
      assertThat(pendingCell.pendingChanges?.inCellSanitation).isEqualTo(false)
      assertThat(pendingCell.pendingApprovalRequestId).isNotNull

      val pendingApprovalRequestId = pendingCell.pendingApprovalRequestId!!

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.CELL_SANITATION)
      assertThat(pendingApproval.locationId).isEqualTo(cellToUpdate.id)
      assertThat(pendingApproval.prisonId).isEqualTo(cellToUpdate.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(cellToUpdate.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.inCellSanitation).isEqualTo(false)
      assertThat(pendingApproval.reasonForChange).isEqualTo("The toilet is old")
      assertThat(pendingApproval.locations).isNotEmpty
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].inCellSanitation).isEqualTo(false)

      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      webTestClient.get().uri("/cell-certificates/${approvedRequest.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(approvedRequest.certificateId)
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].inCellSanitation").isEqualTo(false)

      val approvedLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(approvedLocation.inCellSanitation).isEqualTo(false)
      assertThat(approvedLocation.pendingChanges).isNull()
      assertThat(approvedLocation.pendingApprovalRequestId).isNull()
    }
  }
}
