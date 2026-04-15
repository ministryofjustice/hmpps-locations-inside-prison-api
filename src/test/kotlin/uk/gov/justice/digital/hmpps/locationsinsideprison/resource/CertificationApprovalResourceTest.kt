package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellMarkChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellSanitationChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ReactivationLocationsApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime

@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationApprovalResourceTest : CommonDataTestBase() {

  @BeforeEach
  fun beforeEach() {
    baselinePrison(leedsWing.prisonId)
  }

  @DisplayName("PUT /locations/{id}/capacity")
  @Nested
  inner class CapacityLocationTest {

    @Test
    fun `can change capacity a location and request approval`() {
      // will return a prisoner for each location under the Leeds wing
      leedsWing.findAllLeafLocations().forEach {
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(it.getPathHierarchy()),
          true,
        )
      }
      val firstCell = leedsWing.findAllLeafLocations().first()
      webTestClient.put().uri("/locations/${firstCell.id}/capacity")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CapacityChangeRequest(
              workingCapacity = 1,
              maxCapacity = 1,
              certifiedNormalAccommodation = 1,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
              {
                "id": "${firstCell.id}",
                "prisonId": "${firstCell.prisonId}",
                "pathHierarchy": "${firstCell.getPathHierarchy()}",
                "locationType": "CELL",
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 1,
                  "certifiedNormalAccommodation": 1
                },
                "pendingChanges": {
                  "maxCapacity": 1,
                  "workingCapacity": 1,
                  "certifiedNormalAccommodation": 1
                },
                "active": true,
                "key": "${firstCell.getKey()}"
              }
          """,
          JsonCompareMode.LENIENT,
        )

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val capacityChangedLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(capacityChangedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_ACTIVE)
      assertThat(capacityChangedLocation.pendingApprovalRequestId).isNotNull
      assertThat(capacityChangedLocation.capacity?.maxCapacity).isEqualTo(2)
      assertThat(capacityChangedLocation.capacity?.workingCapacity).isEqualTo(1)

      val pendingApprovalRequestId = capacityChangedLocation.pendingApprovalRequestId!!

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.CAPACITY_CHANGE)
      assertThat(pendingApproval.locationId).isEqualTo(firstCell.id)
      assertThat(pendingApproval.prisonId).isEqualTo(firstCell.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(firstCell.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(-1)
      assertThat(pendingApproval.workingCapacity).isEqualTo(1)
      assertThat(pendingApproval.maxCapacity).isEqualTo(1)
      assertThat(pendingApproval.certifiedNormalAccommodation).isEqualTo(1)
      assertThat(pendingApproval.locations).isNotNull
      assertThat(pendingApproval.locations).hasSize(1)
      val locationChanged = pendingApproval.locations?.get(0)!!
      assertThat(locationChanged).isNotNull
      assertThat(locationChanged.currentWorkingCapacity).isEqualTo(1)
      assertThat(locationChanged.currentMaxCapacity).isEqualTo(2)
      assertThat(locationChanged.currentCertifiedNormalAccommodation).isEqualTo(1)
      assertThat(locationChanged.workingCapacity).isEqualTo(1)
      assertThat(locationChanged.maxCapacity).isEqualTo(1)
      assertThat(locationChanged.certifiedNormalAccommodation).isEqualTo(1)

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

      getDomainEvents(3).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to firstCell.getKey(),
          "location.inside.prison.amended" to firstCell.getParent()?.getKey(),
          "location.inside.prison.amended" to firstCell.getParent()?.getParent()?.getKey(),
        )
      }

      webTestClient.get().uri("/cell-certificates/${approvedRequest.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(approvedRequest.certificateId)
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(11)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(6)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(6)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(6)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].maxCapacity").isEqualTo(2)

      val approvedCapacityLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedCapacityLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(approvedCapacityLocation.pendingApprovalRequestId).isNull()
      assertThat(approvedCapacityLocation.currentCellCertificate).isNotNull
      assertThat(approvedCapacityLocation.currentCellCertificate!!.maxCapacity).isEqualTo(1)
    }

    @Test
    fun `can change capacity a location and request approval but be stopped by an addition prisoners being moved in`() {
      // will return a prisoner for each location under the Leeds wing
      leedsWing.findAllLeafLocations().forEach {
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(it.getPathHierarchy()),
          true,
        )
      }
      val firstCell = leedsWing.findAllLeafLocations().first()
      webTestClient.put().uri("/locations/${firstCell.id}/capacity")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CapacityChangeRequest(
              workingCapacity = 1,
              maxCapacity = 1,
              certifiedNormalAccommodation = 1,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val pendingApprovalRequestId = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!.pendingApprovalRequestId!!

      prisonerSearchMockServer.resetAll()
      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(firstCell.getPathHierarchy()),
        true,
        numberOfPrisonersInCell = 2,
      )

      assertThat(
        webTestClient.put().uri("/certification/location/approve")
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
          .expectStatus().is4xxClientError
          .expectBody<ErrorResponse>()
          .returnResult().responseBody!!.errorCode,
      ).isEqualTo(ErrorCode.MaxCapacityCannotBeBelowOccupancyLevel.errorCode)

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val approvedCapacityLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedCapacityLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_ACTIVE)
      assertThat(approvedCapacityLocation.pendingApprovalRequestId).isNotNull()
      assertThat(approvedCapacityLocation.currentCellCertificate).isNotNull
      assertThat(approvedCapacityLocation.currentCellCertificate!!.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `can change capacity a location and request withdrawal`() {
      // will return a prisoner for each location under the Leeds wing
      leedsWing.findAllLeafLocations().forEach {
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(it.getPathHierarchy()),
          true,
        )
      }
      val firstCell = leedsWing.findAllLeafLocations().first()
      webTestClient.put().uri("/locations/${firstCell.id}/capacity")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CapacityChangeRequest(
              workingCapacity = 1,
              maxCapacity = 1,
              certifiedNormalAccommodation = 1,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val pendingApprovalRequestId = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!.pendingApprovalRequestId!!

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Do not approve this capacity change, it is not correct.",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(withdrawnRequest.certificateId).isNull()
      assertThat(withdrawnRequest.status).isEqualTo(ApprovalRequestStatus.WITHDRAWN)
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val approvedCapacityLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedCapacityLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(approvedCapacityLocation.pendingApprovalRequestId).isNull()
      assertThat(approvedCapacityLocation.currentCellCertificate).isNotNull
      assertThat(approvedCapacityLocation.currentCellCertificate!!.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `can change just working capacity a location and not required request approval`() {
      // will return a prisoner for each location under the Leeds wing
      leedsWing.findAllLeafLocations().forEach {
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(it.getPathHierarchy()),
          true,
        )
      }
      val firstCell = leedsWing.findAllLeafLocations().first()
      webTestClient.put().uri("/locations/${firstCell.id}/capacity")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CapacityChangeRequest(
              workingCapacity = 2,
              maxCapacity = 2,
              certifiedNormalAccommodation = 1,
              temporaryWorkingCapacityChange = true,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          // language=json
          """
              {
                "id": "${firstCell.id}",
                "prisonId": "${firstCell.prisonId}",
                "pathHierarchy": "${firstCell.getPathHierarchy()}",
                "locationType": "CELL",
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 2,
                  "certifiedNormalAccommodation": 1
                },
                "active": true,
                "key": "${firstCell.getKey()}"
              }
          """,
          JsonCompareMode.LENIENT,
        )

      getDomainEvents(3).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to firstCell.getKey(),
          "location.inside.prison.amended" to firstCell.getParent()?.getKey(),
          "location.inside.prison.amended" to firstCell.getParent()?.getParent()?.getKey(),
        )
      }

      val capacityChangedLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(capacityChangedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(capacityChangedLocation.pendingApprovalRequestId).isNull()
      assertThat(capacityChangedLocation.capacity?.maxCapacity).isEqualTo(2)
      assertThat(capacityChangedLocation.capacity?.workingCapacity).isEqualTo(2)
      assertThat(capacityChangedLocation.currentCellCertificate).isNotNull
      assertThat(capacityChangedLocation.currentCellCertificate!!.workingCapacity).isEqualTo(1)
    }
  }

  @DisplayName("PUT /locations/{id}/deactivate")
  @Nested
  inner class DeactivateLocationTest {

    @Test
    fun `workingCapacityChange is correctly calculated when some cells are already off cert inactive`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell

      val now = LocalDateTime.now(clock)
      val proposedReactivationDate = now.plusMonths(1).toLocalDate()

      // Get one cell and deactivate it first (without approval)
      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(firstCell.getPathHierarchy()),
        false,
      )

      webTestClient.put().uri("/locations/${firstCell.id}/deactivate/temporary")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            TemporaryDeactivationLocationRequest(
              deactivationReason = DeactivatedReason.DAMAGED,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      getDomainEvents(1)

      // Now deactivate the wing (which should still show full working capacity change for ALL cells, including already-inactive ones)
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
              reasonForChange = "Wing is being refurbished",
              deactivationReason = DeactivatedReason.MOTHBALLED,
              proposedReactivationDate = proposedReactivationDate,
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

      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

      getDomainEvents(8) // 8 locations because 1 was already deactivated

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      // Even though one cell was already inactive, the workingCapacityChange should still be -6
      // because we're reporting the total change from the current working capacity from the certificate
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(-6)
    }

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
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo("The cell as been flooded")

      val firstCell = leedsWing.findAllLeafLocations().first()
      val firstApprovedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(firstApprovedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)

      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

      val results = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
      getDomainEvents(results.size).let { messages ->
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
      assertThat(pendingApproval.locations!![0].subLocations).hasSize(2)
      assertThat(pendingApproval.locations[0].subLocations!![0].currentWorkingCapacity).isEqualTo(3)
      assertThat(pendingApproval.locations[0].subLocations!![0].workingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].subLocations!![1].currentWorkingCapacity).isEqualTo(3)
      assertThat(pendingApproval.locations[0].subLocations!![0].workingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].subLocations!![0].subLocations).hasSize(3)
      assertThat(pendingApproval.locations[0].subLocations!![1].subLocations).hasSize(3)
      assertThat(pendingApproval.locations[0].subLocations!![0].subLocations!![0].currentWorkingCapacity).isEqualTo(1)
      assertThat(pendingApproval.locations[0].subLocations!![0].subLocations!![0].workingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].subLocations!![0].subLocations!![0].maxCapacity).isEqualTo(2)
      assertThat(pendingApproval.locations[0].subLocations!![1].subLocations!![0].currentWorkingCapacity).isEqualTo(1)
      assertThat(pendingApproval.locations[0].subLocations!![1].subLocations!![0].workingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].subLocations!![1].subLocations!![0].maxCapacity).isEqualTo(2)

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
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo("The cell as been flooded")

      val approvedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(approvedDeactivatedCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(approvedDeactivatedCell.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(approvedDeactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(approvedDeactivatedCell.currentCellCertificate).isNotNull
      assertThat(approvedDeactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(0)
      assertThat(approvedDeactivatedCell.lastDeactivationReasonForChange).isEqualTo("The cell as been flooded")
    }

    @Test
    fun `can deactivate a location and reject approval`() {
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
      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

      getDomainEvents(9).let { messages ->
        val results = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *results.map { "location.inside.prison.deactivated" to it }.toTypedArray(),
        )
      }

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Deactivation refused",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejectedRequest.certificateId).isNull()
      assertThat(rejectedRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)

      val rejectedDeactivatedLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedDeactivatedLocation.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(rejectedDeactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(rejectedDeactivatedLocation.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(rejectedDeactivatedLocation.pendingApprovalRequestId).isNull()
      assertThat(rejectedDeactivatedLocation.lastDeactivationReasonForChange).isNull()
    }
  }

  @DisplayName("PUT /locations/{id}/reactivate")
  @Nested
  inner class ReactivateLocationTest {

    @Test
    fun `can request to reactivate a location with cascade and then approve`() {
      deactivateOffCert()
      webTestClient.put().uri("/certification/location/reactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ReactivationLocationsApprovalRequest(
              topLevelLocationId = leedsWing.id!!,
              cascadeReactivation = true,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val pendingReactivationLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(pendingReactivationLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      val pendingApprovalRequestId = pendingReactivationLocation.pendingApprovalRequestId!!

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.REACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(leedsWing.id)
      assertThat(pendingApproval.prisonId).isEqualTo(leedsWing.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(leedsWing.getKey())
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].subLocations).hasSize(2)
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations[0].currentWorkingCapacity).isEqualTo(6)
      assertThat(pendingApproval.locations[0].currentMaxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].currentCertifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].workingCapacity).isEqualTo(6)
      assertThat(pendingApproval.locations[0].maxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].certifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].subLocations).hasSize(2)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations).hasSize(3)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.currentSpecialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ESCAPE_LIST,
      )

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

      val reactivatedLocations = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
      getDomainEvents(reactivatedLocations.size * 2).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *reactivatedLocations.map { "location.inside.prison.reactivated" to it }.toTypedArray(),
          *reactivatedLocations.map { "location.inside.prison.amended" to it }.toTypedArray(),
        )
      }

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
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(6)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].specialistCellTypes").isEqualTo("ESCAPE_LIST")
    }

    @Test
    fun `can request to reactivate a locations explicitly and then approve`() {
      deactivateOffCert()
      webTestClient.put().uri("/certification/location/reactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ReactivationLocationsApprovalRequest(
              topLevelLocationId = leedsWing.id!!,
              cellReactivationChanges = leedsWing.cellLocations().associate {
                it.id!! to CellReactivationDetail(
                  capacity = Capacity(
                    workingCapacity = 2,
                    maxCapacity = 2,
                    certifiedNormalAccommodation = 2,
                  ),
                  specialistCellTypes = if (it.getPathHierarchy() == "A-1-001") {
                    emptySet()
                  } else {
                    setOf(SpecialistCellType.SAFE_CELL, SpecialistCellType.CONSTANT_SUPERVISION)
                  },
                )
              },
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val pendingReactivationLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(pendingReactivationLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      val pendingApprovalRequestId = pendingReactivationLocation.pendingApprovalRequestId!!

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.REACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(leedsWing.id)
      assertThat(pendingApproval.prisonId).isEqualTo(leedsWing.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(leedsWing.getKey())
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].subLocations).hasSize(2)
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(6)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(6)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.locations[0].currentWorkingCapacity).isEqualTo(6)
      assertThat(pendingApproval.locations[0].currentMaxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].currentCertifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].workingCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].maxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].certifiedNormalAccommodation).isEqualTo(12)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.currentSpecialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ESCAPE_LIST,
      )
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.specialistCellTypes).isNull()
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(1)?.currentSpecialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ESCAPE_LIST,
      )
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(1)?.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.SAFE_CELL,
        SpecialistCellType.CONSTANT_SUPERVISION,
      )
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

      val reactivatedLocations = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
      getDomainEvents(reactivatedLocations.size * 2).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *reactivatedLocations.map { "location.inside.prison.reactivated" to it }.toTypedArray(),
          *reactivatedLocations.map { "location.inside.prison.amended" to it }.toTypedArray(),
        )
      }

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
        .jsonPath("$.totalWorkingCapacity").isEqualTo(12)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(12)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(12)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].certifiedNormalAccommodation").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].specialistCellTypes").doesNotExist()
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].workingCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].certifiedNormalAccommodation").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].specialistCellTypes").isEqualTo(listOf("CONSTANT_SUPERVISION", "SAFE_CELL"))
    }

    @Test
    fun `can request to reactivate a locations explicitly and then reject`() {
      deactivateOffCert()
      webTestClient.put().uri("/certification/location/reactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ReactivationLocationsApprovalRequest(
              topLevelLocationId = leedsWing.id!!,
              cellReactivationChanges = leedsWing.cellLocations().associate {
                it.id!! to CellReactivationDetail(
                  capacity = Capacity(
                    workingCapacity = 2,
                    maxCapacity = 2,
                    certifiedNormalAccommodation = 2,
                  ),
                  specialistCellTypes = setOf(SpecialistCellType.SAFE_CELL, SpecialistCellType.CONSTANT_SUPERVISION),
                )
              },
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val pendingApprovalRequestId = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!.pendingApprovalRequestId!!

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "I don't want to approve this",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejectedRequest.certificateId).isNull()
      assertThat(rejectedRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val rejectedDeactivatedLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedDeactivatedLocation.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(rejectedDeactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(rejectedDeactivatedLocation.pendingApprovalRequestId).isNull()
      assertThat(rejectedDeactivatedLocation.lastDeactivationReasonForChange).isNull()
    }

    private fun deactivateOffCert() {
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
              reasonForChange = "The wing has been flooded",
              deactivationReason = DeactivatedReason.MOTHBALLED,
              proposedReactivationDate = proposedReactivationDate,
              planetFmReference = "11111",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
      val deactivatedLocations = leedsWing.findSubLocations().map { it.getKey() }.plus(leedsWing.getKey())
      getDomainEvents(deactivatedLocations.size).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *deactivatedLocations.map { "location.inside.prison.deactivated" to it }.toTypedArray(),
        )
      }
    }
  }

  @DisplayName("PUT /locations/residential/{id}/cell-mark-change")
  @Nested
  inner class CellMarkChangeTest {

    @Test
    fun `cannot change a cell mark to an existing cell mark at same level`() {
      val cellToUpdate = leedsWing.findAllLeafLocations().first() as Cell
      val existingCell = leedsWing.findAllLeafLocations().last() as Cell

      assertThat(
        webTestClient.put().uri("/locations/residential/${cellToUpdate.id}/cell-mark-change")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              CellMarkChangeRequest(
                reasonForChange = "This door number already exists",
                cellMark = existingCell.cellMark!!,
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
          .expectBody<ErrorResponse>()
          .returnResult().responseBody!!.errorCode,
      ).isEqualTo(ErrorCode.DuplicateCellMarkAtSameLevel.errorCode)
    }

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

    @Test
    fun `can change a cell mark on a location and reject approval`() {
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

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "The door number is wrong",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejectedRequest.certificateId).isNull()
      assertThat(rejectedRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)

      val rejectedLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(rejectedLocation.cellMark).isEqualTo(cellToUpdate.cellMark)
      assertThat(rejectedLocation.pendingChanges).isNull()
      assertThat(rejectedLocation.pendingApprovalRequestId).isNull()
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
      assertThat(pendingApproval.currentInCellSanitation).isEqualTo(true)
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

    @Test
    fun `can change a cell sanitation on a location and reject approval`() {
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
      val pendingApprovalRequestId = pendingCell.pendingApprovalRequestId!!
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "It does have a toilet!",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejectedRequest.certificateId).isNull()
      assertThat(rejectedRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)

      val rejectedLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(rejectedLocation.inCellSanitation).isEqualTo(true)
      assertThat(rejectedLocation.pendingChanges).isNull()
      assertThat(rejectedLocation.pendingApprovalRequestId).isNull()
    }
  }
}
