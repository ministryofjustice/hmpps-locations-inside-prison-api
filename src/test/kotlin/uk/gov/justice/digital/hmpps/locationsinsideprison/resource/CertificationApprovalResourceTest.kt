package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellMarkChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellSanitationChangeRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.InactiveStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PermanentDeactivationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ReactivationLocationsApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SignedOpCapApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SpecialistCellTypeApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationApprovalResourceTest : CommonDataTestBase() {

  @BeforeEach
  fun beforeEach() {
    super.setUp()
    baselinePrison(leedsWing.prisonId)
  }

  @DisplayName("PUT /certification/location/request-approval")
  @Nested
  inner class DraftApprovalTest {
    @Test
    fun `can approval a draft wing`() {
      // Create a new wing in Leeds prison
      val newWing = resiRepository.saveAndFlush(
        CreateEntireWingRequest(
          prisonId = "LEI",
          wingCode = "Y",
          numberOfCellsPerSection = 3,
          numberOfLandings = 2,
          numberOfSpurs = 0,
          defaultWorkingCapacity = 1,
          defaultMaxCapacity = 2,
          wingDescription = "Wing Y",
        ).toEntity(
          createdBy = EXPECTED_USERNAME,
          clock = clock,
          linkedTransaction = linkedTransactionRepository.saveAndFlush(
            LinkedTransaction(
              prisonId = "LEI",
              transactionType = TransactionType.LOCATION_CREATE,
              transactionDetail = "New Wing Y in Leeds",
              transactionInvokedBy = EXPECTED_USERNAME,
              txStartTime = LocalDateTime.now(clock).minusDays(1),
            ),
          ),
          createInDraft = true,
          specialistCellType = SpecialistCellType.ESCAPE_LIST,
        ),
      )

      // Request approval for the wing
      val pendingApproval = webTestClient.put().uri("/certification/location/request-approval")
        .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            LocationApprovalRequest(
              locationId = newWing.id!!,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.DRAFT)
      assertThat(pendingApproval.locationId).isEqualTo(newWing.id!!)
      assertThat(pendingApproval.prisonId).isEqualTo(newWing.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(newWing.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(6)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(6)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(12)
      assertThat(pendingApproval.locations).isNotNull
      assertThat(pendingApproval.locations).hasSize(1)

      // Approve the request to generate a cell certificate
      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = pendingApproval.id,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      val locationsAndSubLocations = listOf(newWing) + newWing.findSubLocations()
      val expectedEvents = locationsAndSubLocations.map { "location.inside.prison.created" to it.getKey() }

      getDomainEvents(expectedEvents.size).let { messages ->
        assertThat(messages.map { it.eventType to it.additionalInformation?.key })
          .containsExactlyInAnyOrder(*expectedEvents.toTypedArray())
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
        .jsonPath("$.totalMaxCapacity").isEqualTo(24)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(12)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(12)

      val approvedNewWing = webTestClient.get().uri("/locations/${newWing.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedNewWing.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(approvedNewWing.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(approvedNewWing.deactivatedReason).isEqualTo(DeactivatedReason.NEW_BUILD)
      assertThat(approvedNewWing.planetFmReference).isNull()
      assertThat(approvedNewWing.proposedReactivationDate).isNull()
      assertThat(approvedNewWing.pendingApprovalRequestId).isNull()
      assertThat(approvedNewWing.lastDeactivationReasonForChange).isNull()
      assertThat(approvedNewWing.pendingApprovalRequestId).isNull()
      assertThat(approvedNewWing.currentCellCertificate).isNotNull

      // activate the wing
      webTestClient.put().uri("/locations/${newWing.id}/reactivate?cascade-reactivation=true&force-reactivation=true")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      val activeApprovedNewWing = webTestClient.get().uri("/locations/${newWing.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(activeApprovedNewWing.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(activeApprovedNewWing.inactiveStatus).isNull()
      assertThat(activeApprovedNewWing.deactivatedReason).isNull()
    }
  }

  @DisplayName("PUT /locations/{id}/capacity")
  @Nested
  inner class CapacityLocationTest {

    @Test
    fun `can change capacity a location and request approval`() {
      val firstCell = leedsWing.findAllLeafLocations().first()
      val capacityChangedLocation = updateCapacity(cellInLeeds = firstCell.getPathHierarchy(), workingCapacity = 1, maxCapacity = 1, cna = 1, temporaryWorkingCapacityChange = false)

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
          *firstCell.getParentLocations().map { "location.inside.prison.amended" to it.getKey() }.toTypedArray(),
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
      val firstCell = leedsWing.findAllLeafLocations().first()
      val pendingApprovalRequestId = updateCapacity(cellInLeeds = firstCell.getPathHierarchy(), workingCapacity = 1, maxCapacity = 1, cna = 1, temporaryWorkingCapacityChange = false).pendingApprovalRequestId!!

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
      val firstCell = updateCapacity(
        workingCapacity = 1,
        maxCapacity = 1,
        temporaryWorkingCapacityChange = false,
      )

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = firstCell.pendingApprovalRequestId!!,
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
    fun `can change capacity a location and reject`() {
      val firstCell = updateCapacity(
        workingCapacity = 1,
        maxCapacity = 1,
        temporaryWorkingCapacityChange = false,
      )

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = firstCell.pendingApprovalRequestId!!,
              comments = "Capacity change not authorised.",
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

      val rejectedCapacityLocation = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedCapacityLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(rejectedCapacityLocation.pendingApprovalRequestId).isNull()
      assertThat(rejectedCapacityLocation.currentCellCertificate).isNotNull
      assertThat(rejectedCapacityLocation.currentCellCertificate!!.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `can change just working capacity a location and not require a request approval`() {
      val capacityChangedLocation = updateCapacity()

      assertThat(capacityChangedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(capacityChangedLocation.pendingApprovalRequestId).isNull()
      assertThat(capacityChangedLocation.capacity?.maxCapacity).isEqualTo(2)
      assertThat(capacityChangedLocation.capacity?.workingCapacity).isEqualTo(2)
      assertThat(capacityChangedLocation.currentCellCertificate).isNotNull
      assertThat(capacityChangedLocation.currentCellCertificate!!.workingCapacity).isEqualTo(1)
    }

    @Test
    fun `can change a working capacity to match a capacity on a certificate`() {
      val firstCell = updateCapacity(workingCapacity = 2, maxCapacity = 2, temporaryWorkingCapacityChange = true)
      assertThat(firstCell.pendingChanges).isNull()

      // Now reflect on the cert the same values
      val updatedCell = updateCapacity(workingCapacity = 2, maxCapacity = 2, temporaryWorkingCapacityChange = false)
      assertThat(updatedCell.pendingChanges?.workingCapacity).isEqualTo(2)
      assertThat(updatedCell.pendingChanges?.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `can change a working capacity to match a capacity on a certificate but not update existing temporary capacity changes`() {
      // existing temp change on cell A-1-001
      updateCapacity(cellInLeeds = "A-1-001", workingCapacity = 2, maxCapacity = 2, temporaryWorkingCapacityChange = true)

      val oldCertificate = webTestClient.get().uri("/cell-certificates//prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      assertThat(oldCertificate.findLocationInCertificate("A-1-001")!!.workingCapacity).isEqualTo(1)
      assertThat(oldCertificate.findLocationInCertificate("A-1-002")!!.workingCapacity).isEqualTo(1)

      // change a different cell and request approval
      val cellForApproval = updateCapacity(cellInLeeds = "A-1-002", workingCapacity = 2, maxCapacity = 2, cna = 1, temporaryWorkingCapacityChange = false)

      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = cellForApproval.pendingApprovalRequestId!!,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(3)
      getDomainEvents(3)

      val certificate = webTestClient.get().uri("/cell-certificates//prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      assertThat(certificate.findLocationInCertificate("A-1-001")!!.workingCapacity).isEqualTo(1)
      assertThat(certificate.findLocationInCertificate("A-1-002")!!.workingCapacity).isEqualTo(2)
    }

    private fun updateCapacity(
      cellInLeeds: String? = null,
      workingCapacity: Int = 2,
      maxCapacity: Int = 2,
      temporaryWorkingCapacityChange: Boolean = true,
      cna: Int = 1,
    ): Location {
      // will return a prisoner for each location under the Leeds wing
      leedsWing.findAllLeafLocations().forEach {
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(it.getPathHierarchy()),
          true,
        )
      }
      val cellToChange = cellInLeeds?.let { cellPath -> leedsWing.findLocation("LEI-$cellPath") } ?: leedsWing.findAllLeafLocations().first()
      webTestClient.put().uri("/locations/${cellToChange.id}/capacity")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            CapacityChangeRequest(
              workingCapacity = workingCapacity,
              maxCapacity = maxCapacity,
              certifiedNormalAccommodation = cna,
              temporaryWorkingCapacityChange = temporaryWorkingCapacityChange,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      if (temporaryWorkingCapacityChange) {
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to cellToChange.getKey(),
            *cellToChange.getParentLocations().map { location -> "location.inside.prison.amended" to location.getKey() }
              .toTypedArray(),
          )
        }
      } else {
        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero
      }

      return webTestClient.get().uri("/locations/${cellToChange.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!
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

      val inactiveCells = webTestClient.get().uri("/locations/prison/${leedsWing.prisonId}/inactive-cells")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<Location>>()
        .returnResult().responseBody!!

      assertThat(inactiveCells).hasSize(6)
      assertThat(inactiveCells.map { it.getKey() to it.inactiveStatus }).containsExactlyInAnyOrder(
        "LEI-A-1-001" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
        "LEI-A-1-002" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
        "LEI-A-1-003" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
        "LEI-A-2-001" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
        "LEI-A-2-002" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
        "LEI-A-2-003" to InactiveStatus.INACTIVE_PEND_CHANGE_REQ,
      )
    }

    @Test
    fun `can deactivate a cell and request approval`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      val reasonForChange = "The cell has been flooded"
      val deactivatedLocation = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        requiresApproval = true,
        reasonForChange = reasonForChange,
      )

      assertThat(deactivatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(deactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(deactivatedLocation.pendingApprovalRequestId).isNotNull
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo(reasonForChange)
      assertThat(deactivatedLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)

      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.DEACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(firstCell.id)
      assertThat(pendingApproval.prisonId).isEqualTo(firstCell.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(firstCell.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(-1)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.reasonForChange).isEqualTo(reasonForChange)
      assertThat(pendingApproval.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].subLocations).isNull()

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
        .jsonPath("$.prisonId").isEqualTo(firstCell.prisonId)
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(12)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(5)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(6)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(5)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(0)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.locations[0].subLocations[1].subLocations[0].maxCapacity").isEqualTo(2)

      val approvedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(approvedDeactivatedCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(approvedDeactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(approvedDeactivatedCell.currentCellCertificate).isNotNull
      assertThat(approvedDeactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(0)
      assertThat(approvedDeactivatedCell.lastDeactivationReasonForChange).isEqualTo(reasonForChange)
      assertThat(approvedDeactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)

      val inactiveCells = webTestClient.get().uri("/locations/prison/${leedsWing.prisonId}/inactive-cells")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<Location>>()
        .returnResult().responseBody!!

      assertThat(inactiveCells).hasSize(1)
      assertThat(inactiveCells.map { it.inactiveStatus }).containsExactlyInAnyOrder(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)
    }

    @Test
    fun `can change cell cert capacity for deactivated cell`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      var deactivatedLocation = deactivateLocation(firstCell, deactivatedReason = DeactivatedReason.DAMAGED)

      assertThat(deactivatedLocation.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(deactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.DAMAGED)
      assertThat(deactivatedLocation.planetFmReference).isNull()
      assertThat(deactivatedLocation.proposedReactivationDate).isNull()
      assertThat(deactivatedLocation.pendingApprovalRequestId).isNull()
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isNull()
      assertThat(deactivatedLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)

      val proposedReactivationDate = LocalDateTime.now(clock).plusMonths(1).toLocalDate()
      deactivatedLocation = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "2343434",
        proposedReactivationDate = proposedReactivationDate,
        requiresApproval = true,
        reasonForChange = "apply to certificate",
      )

      assertThat(deactivatedLocation.pendingApprovalRequestId).isNotNull
      assertThat(deactivatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(deactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(deactivatedLocation.planetFmReference).isEqualTo("2343434")
      assertThat(deactivatedLocation.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo("apply to certificate")
      assertThat(deactivatedLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)
    }

    @Test
    fun `can deactivate a wing and sublocations and request approval`() {
      val deactivatedLocation = deactivateLocation(
        leedsWing,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        requiresApproval = true,
        reasonForChange = "The wing is being renovated",
      )

      assertThat(deactivatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(deactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(deactivatedLocation.pendingApprovalRequestId).isNotNull
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo("The wing is being renovated")

      val firstCell = leedsWing.findAllLeafLocations().first()
      val firstApprovedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(firstApprovedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(firstApprovedDeactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)

      val pendingApprovalRequestId = deactivatedLocation.pendingApprovalRequestId!!

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
      assertThat(pendingApproval.reasonForChange).isEqualTo("The wing is being renovated")
      assertThat(pendingApproval.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
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
      assertThat(approvedDeactivatedLocation.pendingApprovalRequestId).isNull()
      assertThat(deactivatedLocation.lastDeactivationReasonForChange).isEqualTo("The wing is being renovated")

      val approvedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(approvedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(approvedDeactivatedCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(approvedDeactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(approvedDeactivatedCell.currentCellCertificate).isNotNull
      assertThat(approvedDeactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(0)
      assertThat(approvedDeactivatedCell.lastDeactivationReasonForChange).isEqualTo("The wing is being renovated")
      assertThat(approvedDeactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)

      val inactiveCells = webTestClient.get().uri("/locations/prison/${leedsWing.prisonId}/inactive-cells")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<Location>>()
        .returnResult().responseBody!!

      assertThat(inactiveCells).hasSize(6)
      assertThat(inactiveCells.map { it.getKey() to it.inactiveStatus }).containsExactlyInAnyOrder(
        "LEI-A-1-001" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
        "LEI-A-1-002" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
        "LEI-A-1-003" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
        "LEI-A-2-001" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
        "LEI-A-2-002" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
        "LEI-A-2-003" to InactiveStatus.INACTIVE_MATCHING_CELL_CERT,
      )
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

      val firstCell = leedsWing.findAllLeafLocations().first()
      val rejectedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(rejectedDeactivatedCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(rejectedDeactivatedCell.proposedReactivationDate).isEqualTo(proposedReactivationDate)
      assertThat(rejectedDeactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(rejectedDeactivatedCell.currentCellCertificate).isNotNull
      assertThat(rejectedDeactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(1)
      assertThat(rejectedDeactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)

      val inactiveCells = webTestClient.get().uri("/locations/prison/${leedsWing.prisonId}/inactive-cells")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<Location>>()
        .returnResult().responseBody!!

      assertThat(inactiveCells).hasSize(6)
      assertThat(inactiveCells.map { it.getKey() to it.inactiveStatus }).containsExactlyInAnyOrder(
        "LEI-A-1-001" to InactiveStatus.INACTIVE_TEMP,
        "LEI-A-1-002" to InactiveStatus.INACTIVE_TEMP,
        "LEI-A-1-003" to InactiveStatus.INACTIVE_TEMP,
        "LEI-A-2-001" to InactiveStatus.INACTIVE_TEMP,
        "LEI-A-2-002" to InactiveStatus.INACTIVE_TEMP,
        "LEI-A-2-003" to InactiveStatus.INACTIVE_TEMP,
      )
    }

    @Test
    fun `withdrawing a deactivation approval flips the cell back to short term inactive`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      val deactivatedCell = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        requiresApproval = true,
        reasonForChange = "The cell has been flooded",
      )

      assertThat(deactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)
      val pendingApprovalRequestId = deactivatedCell.pendingApprovalRequestId!!

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Operator changed their mind",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(withdrawnRequest.status).isEqualTo(ApprovalRequestStatus.WITHDRAWN)
      assertThat(withdrawnRequest.certificateId).isNull()
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val withdrawnCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(withdrawnCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(withdrawnCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(withdrawnCell.pendingApprovalRequestId).isNull()
      assertThat(withdrawnCell.currentCellCertificate).isNotNull
      assertThat(withdrawnCell.currentCellCertificate!!.workingCapacity).isEqualTo(1)
      assertThat(withdrawnCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
    }

    @Test
    fun `short term inactive cell can be re-deactivated with approval and aligned to cell cert`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell

      val shortTermDeactivated = deactivateLocation(firstCell, deactivatedReason = DeactivatedReason.DAMAGED)
      assertThat(shortTermDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(shortTermDeactivated.currentCellCertificate!!.workingCapacity).isEqualTo(1)

      val alignedDeactivated = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "PFM-1",
        requiresApproval = true,
        reasonForChange = "Aligning with cell certificate",
        approveDeactivation = true,
      )

      assertThat(alignedDeactivated.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(alignedDeactivated.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(alignedDeactivated.pendingApprovalRequestId).isNull()
      assertThat(alignedDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)
      assertThat(alignedDeactivated.currentCellCertificate).isNotNull
      assertThat(alignedDeactivated.currentCellCertificate!!.workingCapacity).isEqualTo(0)
    }

    @Test
    fun `workingCapacityChange in approval request reflects certificate value when deactivating a temporarilyOffCellCert cell to align with the certificate`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell

      // Short-term deactivate the cell without approval — this sets temporarilyOffCellCert = true.
      // The cell's effective working capacity becomes 0, but the certificate still records WC = 1.
      val shortTermDeactivated = deactivateLocation(firstCell, deactivatedReason = DeactivatedReason.DAMAGED)
      assertThat(shortTermDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(shortTermDeactivated.currentCellCertificate!!.workingCapacity).isEqualTo(1)

      // Now re-deactivate with requiresApproval = true so the deactivation is aligned to the certificate.
      // The pending approval should record the change from the certificate value (1) to 0,
      // not from the effective value (0) to 0.
      val pendingDeactivated = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "PFM-1",
        requiresApproval = true,
        reasonForChange = "Aligning with cell certificate",
        approveDeactivation = false,
      )

      assertThat(pendingDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)
      val pendingApprovalRequestId = pendingDeactivated.pendingApprovalRequestId!!

      val pendingApproval = webTestClient.get().uri("/certification/request-approvals/$pendingApprovalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.DEACTIVATION)
      // workingCapacityChange must reflect the delta from the certificate value (1) to 0, i.e. -1
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(-1)
      assertThat(pendingApproval.locations).hasSize(1)
      // currentWorkingCapacity must reflect the certificate value (1), not the effective value (0)
      assertThat(pendingApproval.locations!![0].currentWorkingCapacity).isEqualTo(1)
      assertThat(pendingApproval.locations!![0].workingCapacity).isEqualTo(0)
    }
  }

  @DisplayName("PUT /certification/location/permanent-deactivation-request-approval")
  @Nested
  inner class PermanentDeactivateLocationTest {

    private fun requestPermanentDeactivation(location: ResidentialLocation, reason: String): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/location/permanent-deactivation-request-approval")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(PermanentDeactivationApprovalRequestDto(locationId = location.id!!, reason = reason)))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun getLocation(id: UUID): Location = webTestClient.get().uri("/locations/$id?includeCurrentCertificate=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!

    @Test
    fun `cannot request permanent deactivation when prison does not require certification approval`() {
      // cell1 is in MDI which does not have certificationApprovalRequired=true
      webTestClient.put().uri("/certification/location/permanent-deactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(PermanentDeactivationApprovalRequestDto(locationId = cell1.id!!, reason = "Demolished")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.LocationDoesNotRequireApproval.errorCode)
    }

    @Test
    fun `cannot request permanent deactivation on an active location`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      webTestClient.put().uri("/certification/location/permanent-deactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(PermanentDeactivationApprovalRequestDto(locationId = firstCell.id!!, reason = "Demolished")))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.LocationCannotBePermanentlyDeactivated.errorCode)
    }

    @Test
    fun `can request and approve permanent deactivation removing the cell from the certificate`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      // Cell must already be temporarily deactivated
      deactivateLocation(firstCell, DeactivatedReason.DAMAGED)

      val pendingApproval = requestPermanentDeactivation(firstCell, "Cell demolished")
      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.PERMANENT_DEACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(firstCell.id)
      assertThat(pendingApproval.locationKey).isEqualTo(firstCell.getKey())
      assertThat(pendingApproval.locations).hasSize(1)
      // preview shows the value the cell currently holds in the certificate
      assertThat(pendingApproval.locations!![0].currentWorkingCapacity).isEqualTo(1)

      assertThat(getLocation(firstCell.id!!).pendingApprovalRequestId).isEqualTo(pendingApproval.id)

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingApproval.id)))
        .exchange()
        .expectStatus().isOk

      getDomainEvents(1).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key })
          .containsExactly("location.inside.prison.deactivated" to firstCell.getKey())
      }

      val archivedCell = getLocation(firstCell.id!!)
      assertThat(archivedCell.permanentlyInactive).isTrue()
      assertThat(archivedCell.pendingApprovalRequestId).isNull()
      // removed from the cell certificate
      assertThat(archivedCell.currentCellCertificate).isNull()

      // current certificate no longer contains the archived cell
      webTestClient.get().uri("/cell-certificates/prison/${leedsWing.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$..pathHierarchy").value<List<String>> { paths ->
          assertThat(paths).doesNotContain(firstCell.getPathHierarchy())
        }
    }

    @Test
    fun `can request and approve permanent deactivation of a whole wing removing it and its sub-locations from the certificate`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      // The wing (and its sub-locations) must already be temporarily deactivated
      deactivateLocation(leedsWing, DeactivatedReason.DAMAGED)

      val pendingApproval = requestPermanentDeactivation(leedsWing, "Wing demolished")
      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.PERMANENT_DEACTIVATION)
      assertThat(pendingApproval.locationId).isEqualTo(leedsWing.id)
      assertThat(pendingApproval.locationKey).isEqualTo(leedsWing.getKey())
      // preview includes the wing with its sub-location hierarchy
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].subLocations).isNotEmpty

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingApproval.id)))
        .exchange()
        .expectStatus().isOk

      getDomainEvents(1).let {
        assertThat(it.map { message -> message.eventType to message.additionalInformation?.key })
          .containsExactly("location.inside.prison.deactivated" to leedsWing.getKey())
      }

      // the wing and the cells below it are all permanently deactivated
      assertThat(getLocation(leedsWing.id!!).permanentlyInactive).isTrue()
      val archivedCell = getLocation(firstCell.id!!)
      assertThat(archivedCell.permanentlyInactive).isTrue()
      assertThat(archivedCell.currentCellCertificate).isNull()

      // current certificate no longer contains the wing or any location below it
      webTestClient.get().uri("/cell-certificates/prison/${leedsWing.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$..pathHierarchy").value<List<String>> { paths ->
          assertThat(paths).noneMatch { it == leedsWing.getPathHierarchy() || it.startsWith("${leedsWing.getPathHierarchy()}-") }
        }
    }

    @Test
    fun `rejecting permanent deactivation leaves the cell in its existing inactive status`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      deactivateLocation(firstCell, DeactivatedReason.DAMAGED)

      val pendingApproval = requestPermanentDeactivation(firstCell, "Cell demolished")

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingApproval.id, comments = "Not demolished after all")))
        .exchange()
        .expectStatus().isOk

      val cell = getLocation(firstCell.id!!)
      assertThat(cell.permanentlyInactive).isFalse()
      assertThat(cell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(cell.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `withdrawing permanent deactivation leaves the cell in its existing inactive status`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      deactivateLocation(firstCell, DeactivatedReason.DAMAGED)

      val pendingApproval = requestPermanentDeactivation(firstCell, "Cell demolished")

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingApproval.id, comments = "Changed our mind")))
        .exchange()
        .expectStatus().isOk

      val cell = getLocation(firstCell.id!!)
      assertThat(cell.permanentlyInactive).isFalse()
      assertThat(cell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(cell.pendingApprovalRequestId).isNull()
    }
  }

  @DisplayName("PUT /locations/{id}/reactivate")
  @Nested
  inner class ReactivateLocationTest {

    @Test
    fun `can request to reactivate a cell that was short term deactivated`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      val deactivatedCell = deactivateLocation(
        firstCell,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = false,
        approveDeactivation = false,
      )

      assertThat(deactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(deactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(deactivatedCell.currentCellCertificate).isNotNull
      assertThat(deactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(1)
      assertThat(deactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)

      webTestClient.put().uri("/locations/${deactivatedCell.id}/reactivate")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      val parentResults = firstCell.getParentLocations().associate {
        it.getKey() to "location.inside.prison.amended"
      }
      val results = mapOf(firstCell.getKey() to "location.inside.prison.reactivated") + parentResults

      getDomainEvents(results.size).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *results.map { it.value to it.key }.toTypedArray(),
        )
      }

      val reactivatedCell = webTestClient.get().uri("/locations/${deactivatedCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(reactivatedCell.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(reactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(reactivatedCell.currentCellCertificate).isNotNull
      assertThat(reactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(1)
      assertThat(reactivatedCell.inactiveStatus).isNull()
    }

    @Test
    fun `cannot request reactivation approval for a short term inactive cell`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      val shortTermDeactivated = deactivateLocation(firstCell, deactivatedReason = DeactivatedReason.DAMAGED)

      assertThat(shortTermDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(shortTermDeactivated.pendingApprovalRequestId).isNull()

      val error = webTestClient.put().uri("/certification/location/reactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ReactivationLocationsApprovalRequest(
              topLevelLocationId = firstCell.id!!,
              cellReactivationChanges = mapOf(
                firstCell.id!! to CellReactivationDetail(
                  capacity = Capacity(workingCapacity = 1, maxCapacity = 2, certifiedNormalAccommodation = 1),
                ),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult().responseBody!!

      assertThat(error.errorCode).isEqualTo(ErrorCode.LocationCannotBeReactivated.errorCode)

      val unchangedCell = webTestClient.get().uri("/locations/${firstCell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(unchangedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(unchangedCell.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `can directly reactivate a cell after its deactivation approval was rejected`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      val pendingDeactivated = deactivateLocation(
        firstCell,
        DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        requiresApproval = true,
        reasonForChange = "Floor damaged",
      )

      assertThat(pendingDeactivated.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)
      val pendingApprovalRequestId = pendingDeactivated.pendingApprovalRequestId!!

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Cell is still serviceable",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val rejectedCell = webTestClient.get().uri("/locations/${firstCell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_TEMP)
      assertThat(rejectedCell.pendingApprovalRequestId).isNull()

      webTestClient.put().uri("/locations/${firstCell.id}/reactivate")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      val parentResults = firstCell.getParentLocations().associate {
        it.getKey() to "location.inside.prison.amended"
      }
      val results = mapOf(firstCell.getKey() to "location.inside.prison.reactivated") + parentResults

      getDomainEvents(results.size).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *results.map { it.value to it.key }.toTypedArray(),
        )
      }

      val reactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(reactivatedCell.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(reactivatedCell.inactiveStatus).isNull()
      assertThat(reactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(reactivatedCell.currentCellCertificate).isNotNull
      assertThat(reactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(1)
    }

    @Test
    fun `can request to reactivate a cell and then approve`() {
      val firstCell = leedsWing.findAllLeafLocations().first() as Cell
      deactivateLocation(
        firstCell,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = true,
        approveDeactivation = true,
      )
      webTestClient.put().uri("/certification/location/reactivation-request-approval")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ReactivationLocationsApprovalRequest(
              topLevelLocationId = firstCell.id!!,
              cellReactivationChanges = mapOf(
                firstCell.id!! to CellReactivationDetail(
                  capacity = Capacity(
                    workingCapacity = 1,
                    maxCapacity = 2,
                    certifiedNormalAccommodation = 1,
                  ),
                  specialistCellTypes = setOf(SpecialistCellType.SAFE_CELL, SpecialistCellType.CONSTANT_SUPERVISION),
                ),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val pendingReactivationLocation = webTestClient.get().uri("/locations/${firstCell.id}")
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
      assertThat(pendingApproval.locationId).isEqualTo(firstCell.id)
      assertThat(pendingApproval.prisonId).isEqualTo(firstCell.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(firstCell.getKey())
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(1)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(0)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations!![0].subLocations).isNull()
      assertThat(pendingApproval.locations[0].currentWorkingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].currentMaxCapacity).isEqualTo(2)
      assertThat(pendingApproval.locations[0].currentCertifiedNormalAccommodation).isEqualTo(1)
      assertThat(pendingApproval.locations[0].workingCapacity).isEqualTo(1)
      assertThat(pendingApproval.locations[0].maxCapacity).isEqualTo(2)
      assertThat(pendingApproval.locations[0].certifiedNormalAccommodation).isEqualTo(1)
      assertThat(pendingApproval.locations[0].currentSpecialistCellTypes).isNull()
      assertThat(pendingApproval.locations[0].specialistCellTypes).containsExactlyInAnyOrder(
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

      val parentResults = firstCell.getParentLocations().plus(firstCell).associate {
        it.getKey() to "location.inside.prison.amended"
      }
      val results = mapOf(firstCell.getKey() to "location.inside.prison.reactivated") + parentResults

      getDomainEvents(results.size).let { messages ->
        assertThat(messages.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
          *results.map { it.value to it.key }.toTypedArray(),
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
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].specialistCellTypes").isEqualTo(listOf("CONSTANT_SUPERVISION", "SAFE_CELL"))

      val reactivatedLocation = webTestClient.get().uri("/locations/key/${firstCell.getKey()}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(reactivatedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(reactivatedLocation.pendingChanges).isNull()
      assertThat(reactivatedLocation.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `can request to reactivate a wing with cascade and then approve`() {
      deactivateLocation(
        leedsWing,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = true,
        approveDeactivation = true,
      )
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
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(6)
      assertThat(pendingApproval.locations).hasSize(1)
      assertThat(pendingApproval.locations[0].currentWorkingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].currentMaxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].currentCertifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].workingCapacity).isEqualTo(6)
      assertThat(pendingApproval.locations[0].maxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].certifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].subLocations).hasSize(2)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations).hasSize(3)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.currentSpecialistCellTypes).isNull()
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
    }

    @Test
    fun `can request to reactivate a wing explicitly and then approve`() {
      deactivateLocation(
        leedsWing,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = true,
        approveDeactivation = true,
      )
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
                  specialistCellTypes = when (it.getPathHierarchy()) {
                    "A-1-001" -> emptySet()
                    "A-1-002" -> null
                    else -> setOf(SpecialistCellType.SAFE_CELL, SpecialistCellType.CONSTANT_SUPERVISION)
                  },
                )
              },
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      val deactivatedCell = webTestClient.get().uri("/locations/key/LEI-A-1-001")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(deactivatedCell.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(deactivatedCell.pendingChanges).isNotNull()
      assertThat(deactivatedCell.pendingApprovalRequestId).isNotNull()

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
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(12)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(6)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(0)
      assertThat(pendingApproval.locations[0].currentWorkingCapacity).isEqualTo(0)
      assertThat(pendingApproval.locations[0].currentMaxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].currentCertifiedNormalAccommodation).isEqualTo(6)
      assertThat(pendingApproval.locations[0].workingCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].maxCapacity).isEqualTo(12)
      assertThat(pendingApproval.locations[0].certifiedNormalAccommodation).isEqualTo(12)
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.currentSpecialistCellTypes).isNull()
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(0)?.specialistCellTypes).isEmpty()
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(1)?.currentSpecialistCellTypes).isNull()
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(1)?.specialistCellTypes).isNull()
      assertThat(pendingApproval.locations[0].subLocations?.get(0)?.subLocations?.get(2)?.specialistCellTypes).containsExactlyInAnyOrder(
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

      val reactivatedLocation = webTestClient.get().uri("/locations/key/LEI-A-1-001")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(reactivatedLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(reactivatedLocation.pendingChanges).isNull()
      assertThat(reactivatedLocation.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `can request to reactivate a locations explicitly and then reject`() {
      deactivateLocation(
        leedsWing,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = true,
        approveDeactivation = true,
      )
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
      assertThat(rejectedDeactivatedLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)
      assertThat(rejectedDeactivatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(rejectedDeactivatedLocation.pendingApprovalRequestId).isNull()
      assertThat(rejectedDeactivatedLocation.lastDeactivationReasonForChange).isEqualTo("The wing has been flooded")

      val firstCell = leedsWing.findAllLeafLocations().first()
      val rejectedDeactivatedCell = webTestClient.get().uri("/locations/${firstCell.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(rejectedDeactivatedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(rejectedDeactivatedCell.deactivatedReason).isEqualTo(DeactivatedReason.MOTHBALLED)
      assertThat(rejectedDeactivatedCell.pendingApprovalRequestId).isNull()
      assertThat(rejectedDeactivatedCell.currentCellCertificate).isNotNull
      assertThat(rejectedDeactivatedCell.currentCellCertificate!!.workingCapacity).isEqualTo(0)
      assertThat(rejectedDeactivatedCell.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)
    }

    @Test
    fun `can withdraw a reactivation approval request`() {
      deactivateLocation(
        leedsWing,
        deactivatedReason = DeactivatedReason.MOTHBALLED,
        planetFmReference = "11111",
        reasonForChange = "The wing has been flooded",
        requiresApproval = true,
        approveDeactivation = true,
      )

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

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Reactivation not needed yet",
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

      val withdrawnLocation = webTestClient.get().uri("/locations/${leedsWing.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(withdrawnLocation.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(withdrawnLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_MATCHING_CELL_CERT)
      assertThat(withdrawnLocation.pendingApprovalRequestId).isNull()
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

      val currentCertAfterReject = webTestClient.get().uri("/cell-certificates/prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterReject = currentCertAfterReject.findLocationInCertificate(cellToUpdate.getPathHierarchy())
      assertThat(cellInCertAfterReject?.cellMark).isEqualTo(cellToUpdate.cellMark)
    }

    @Test
    fun `can change a cell mark on a location and withdraw approval`() {
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
      assertThat(pendingCell.pendingApprovalRequestId).isNotNull
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingCell.pendingApprovalRequestId!!,
              comments = "Cell mark change no longer needed",
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

      val withdrawnLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(withdrawnLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(withdrawnLocation.cellMark).isEqualTo(cellToUpdate.cellMark)
      assertThat(withdrawnLocation.pendingChanges).isNull()
      assertThat(withdrawnLocation.pendingApprovalRequestId).isNull()

      val currentCertAfterWithdraw = webTestClient.get().uri("/cell-certificates/prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterWithdraw = currentCertAfterWithdraw.findLocationInCertificate(cellToUpdate.getPathHierarchy())
      assertThat(cellInCertAfterWithdraw?.cellMark).isEqualTo(cellToUpdate.cellMark)
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

      val currentCertAfterReject = webTestClient.get().uri("/cell-certificates/prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterReject = currentCertAfterReject.findLocationInCertificate(cellToUpdate.getPathHierarchy())
      assertThat(cellInCertAfterReject?.inCellSanitation).isEqualTo(true)
    }

    @Test
    fun `can change a cell sanitation on a location and withdraw approval`() {
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

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingApprovalRequestId,
              comments = "Sanitation change no longer required",
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

      val withdrawnLocation = webTestClient.get().uri("/locations/${cellToUpdate.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(withdrawnLocation.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(withdrawnLocation.inCellSanitation).isEqualTo(true)
      assertThat(withdrawnLocation.pendingChanges).isNull()
      assertThat(withdrawnLocation.pendingApprovalRequestId).isNull()

      val currentCertAfterWithdraw = webTestClient.get().uri("/cell-certificates/prison/LEI/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterWithdraw = currentCertAfterWithdraw.findLocationInCertificate(cellToUpdate.getPathHierarchy())
      assertThat(cellInCertAfterWithdraw?.inCellSanitation).isEqualTo(true)
    }
  }

  private fun deactivateLocation(
    location: ResidentialLocation,
    deactivatedReason: DeactivatedReason,
    proposedReactivationDate: LocalDate? = null,
    planetFmReference: String? = null,
    requiresApproval: Boolean = false,
    reasonForChange: String? = null,
    approveDeactivation: Boolean = false,
  ): Location {
    prisonerSearchMockServer.stubSearchByLocations(
      location.prisonId,
      location.findAllLeafLocations().map { it.getPathHierarchy() },
      false,
    )

    val inactiveStatus = webTestClient.get().uri("/locations/${location.id}")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!.inactiveStatus

    webTestClient.put().uri("/locations/${location.id}/deactivate/temporary")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          TemporaryDeactivationLocationRequest(
            requiresApproval = requiresApproval,
            reasonForChange = reasonForChange,
            deactivationReason = deactivatedReason,
            proposedReactivationDate = proposedReactivationDate,
            planetFmReference = planetFmReference,
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk

    val locationsAndSubLocations = listOf(location) + location.findSubLocations()
    val expectedEvents = if (inactiveStatus == null) {
      locationsAndSubLocations.map { "location.inside.prison.deactivated" to it.getKey() } +
        location.findParentLocations().map { "location.inside.prison.amended" to it.getKey() }
    } else {
      locationsAndSubLocations
        .map { "location.inside.prison.amended" to it.getKey() }
    }

    getDomainEvents(expectedEvents.size).let { messages ->
      assertThat(messages.map { it.eventType to it.additionalInformation?.key })
        .containsExactlyInAnyOrder(*expectedEvents.toTypedArray())
    }

    if (requiresApproval && approveDeactivation) {
      val deactivatedLocation = webTestClient.get().uri("/locations/${location.id}?includeCurrentCertificate=true")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      if (deactivatedLocation.pendingApprovalRequestId != null) {
        webTestClient.put().uri("/certification/location/approve")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = deactivatedLocation.pendingApprovalRequestId,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        val expectedEvents = locationsAndSubLocations
          .map { "location.inside.prison.amended" to it.getKey() } +
          location.findParentLocations().map { "location.inside.prison.amended" to it.getKey() }

        getDomainEvents(expectedEvents.size).let { messages ->
          assertThat(messages.map { it.eventType to it.additionalInformation?.key })
            .containsExactlyInAnyOrder(*expectedEvents.toTypedArray())
        }
      }
    }

    return webTestClient.get().uri("/locations/${location.id}?includeCurrentCertificate=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!
  }

  @DisplayName("PUT /certification/location/specialist-cell-type-change")
  @Nested
  inner class SpecialistCellTypeTest {

    private fun firstLeedsCell(): Cell = leedsWing.findAllLeafLocations().first() as Cell

    private fun requestSpecialistCellTypeApproval(
      cell: Cell,
      specialistCellTypes: Set<SpecialistCellType>,
      workingCapacity: Int = 0,
      maxCapacity: Int = 1,
      certifiedNormalAccommodation: Int = 0,
    ): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/location/specialist-cell-type-change")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          SpecialistCellTypeApprovalRequest(
            locationId = cell.id!!,
            specialistCellTypes = specialistCellTypes,
            workingCapacity = workingCapacity,
            maxCapacity = maxCapacity,
            certifiedNormalAccommodation = certifiedNormalAccommodation,
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun approveRequest(approvalRequestId: java.util.UUID): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = approvalRequestId)))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    @Test
    fun `when cert approval required, adding a capacity-affecting specialist cell type via direct endpoint is rejected`() {
      val cell = firstLeedsCell()

      webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(135)
    }

    @Test
    fun `when cert approval required, removing a capacity-affecting specialist cell type via direct endpoint is rejected`() {
      val cell = firstLeedsCell()

      // First add the type via the approval flow
      val pendingApproval = requestSpecialistCellTypeApproval(cell, setOf(SpecialistCellType.CSU))
      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.SPECIALIST_CELL_TYPE)

      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(cell.getPathHierarchy()),
        false,
      )

      approveRequest(pendingApproval.id)
      getDomainEvents(3)

      val freshCell = cellRepository.findById(cell.id!!).get()

      // Now try to remove it via direct endpoint
      webTestClient.put().uri("/locations/${freshCell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(emptySet<SpecialistCellType>()))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(135)
    }

    @Test
    fun `when cert approval required, swapping capacity-affecting types (same count) via direct endpoint is allowed and updates cert`() {
      val cell = firstLeedsCell()

      // First give cell a capacity-affecting type via approval
      val pendingApproval = requestSpecialistCellTypeApproval(cell, setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST))

      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(cell.getPathHierarchy()),
        false,
      )

      approveRequest(pendingApproval.id)
      getDomainEvents(3)

      // Swap to a different capacity-affecting type via direct endpoint
      val result = webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.CSU)))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(result.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.CSU)

      // Verify the current cert was updated in-place
      val currentCert = webTestClient.get().uri("/cell-certificates/prison/${cell.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCert = currentCert.findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCert).isNotNull
      assertThat(cellInCert?.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.CSU)

      getDomainEvents(1)
    }

    @Test
    fun `when cert approval required, adding a non-capacity-affecting type via direct endpoint is allowed and updates cert`() {
      val cell = firstLeedsCell()

      webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.ACCESSIBLE_CELL)))
        .exchange()
        .expectStatus().isOk

      // Verify cert updated
      val currentCert = webTestClient.get().uri("/cell-certificates/prison/${cell.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCert = currentCert.findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCert?.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.ACCESSIBLE_CELL)

      getDomainEvents(1)
    }

    @Test
    fun `can request approval to add a capacity-affecting specialist cell type`() {
      val cell = firstLeedsCell()

      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST, SpecialistCellType.ACCESSIBLE_CELL),
        workingCapacity = 0,
        maxCapacity = 1,
        certifiedNormalAccommodation = 0,
      )

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.SPECIALIST_CELL_TYPE)
      assertThat(pendingApproval.status).isEqualTo(ApprovalRequestStatus.PENDING)
      assertThat(pendingApproval.locationId).isEqualTo(cell.id)
      assertThat(pendingApproval.prisonId).isEqualTo(cell.prisonId)
      assertThat(pendingApproval.locationKey).isEqualTo(cell.getKey())
      assertThat(pendingApproval.workingCapacity).isEqualTo(0)
      assertThat(pendingApproval.maxCapacity).isEqualTo(1)
      assertThat(pendingApproval.certifiedNormalAccommodation).isEqualTo(0)
      assertThat(pendingApproval.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
        SpecialistCellType.ACCESSIBLE_CELL,
      )
      // currentSpecialistCellTypes reflects the cell's state before this approval
      assertThat(pendingApproval.currentSpecialistCellTypes).isNull()
      assertThat(pendingApproval.locations).hasSize(1)

      // The top-level change totals reflect requested-minus-current (cell defaults: wc=1, mc=2, cna=1).
      assertThat(pendingApproval.workingCapacityChange).isEqualTo(-1)
      assertThat(pendingApproval.maxCapacityChange).isEqualTo(-1)
      assertThat(pendingApproval.certifiedNormalAccommodationChange).isEqualTo(-1)

      // The location within the array must surface the proposed (pending) specialist cell types and
      // the requested capacities, while currentXxx reflects the live cell.
      val locationInRequest = pendingApproval.locations!![0]
      assertThat(locationInRequest.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
        SpecialistCellType.ACCESSIBLE_CELL,
      )
      assertThat(locationInRequest.currentSpecialistCellTypes).isNullOrEmpty()
      assertThat(locationInRequest.workingCapacity).isEqualTo(0)
      assertThat(locationInRequest.maxCapacity).isEqualTo(1)
      assertThat(locationInRequest.certifiedNormalAccommodation).isEqualTo(0)
      assertThat(locationInRequest.currentWorkingCapacity).isEqualTo(1)
      assertThat(locationInRequest.currentMaxCapacity).isEqualTo(2)
      assertThat(locationInRequest.currentCertifiedNormalAccommodation).isEqualTo(1)

      // The cell should now be locked
      val lockedCell = webTestClient.get().uri("/locations/${cell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(lockedCell.status).isEqualTo(DerivedLocationStatus.LOCKED_ACTIVE)
      assertThat(lockedCell.pendingApprovalRequestId).isEqualTo(pendingApproval.id)

      // The pending change surfaces the proposed specialist cell types, while the live value is unchanged.
      assertThat(lockedCell.pendingChanges?.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
        SpecialistCellType.ACCESSIBLE_CELL,
      )
      assertThat(lockedCell.specialistCellTypes).isNullOrEmpty()
    }

    @Test
    fun `request approval surfaces existing specialist cell types as currentSpecialistCellTypes on the location`() {
      val cell = firstLeedsCell()

      // Give the cell an existing (non-capacity-affecting) specialist cell type directly, no approval needed.
      webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.ACCESSIBLE_CELL)))
        .exchange()
        .expectStatus().isOk

      // Now request approval to add a capacity-affecting type alongside the existing one.
      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.ACCESSIBLE_CELL, SpecialistCellType.BIOHAZARD_DIRTY_PROTEST),
        workingCapacity = 0,
        maxCapacity = 1,
        certifiedNormalAccommodation = 0,
      )

      assertThat(pendingApproval.locations).hasSize(1)
      val locationInRequest = pendingApproval.locations!![0]

      // currentSpecialistCellTypes reflects the live cell state (before the change)...
      assertThat(locationInRequest.currentSpecialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ACCESSIBLE_CELL,
      )
      // ...while specialistCellTypes reflects the proposed (pending) state.
      assertThat(locationInRequest.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ACCESSIBLE_CELL,
        SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
      )

      // The top-level object carries the same distinction.
      assertThat(pendingApproval.currentSpecialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ACCESSIBLE_CELL,
      )
      assertThat(pendingApproval.specialistCellTypes).containsExactlyInAnyOrder(
        SpecialistCellType.ACCESSIBLE_CELL,
        SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
      )
    }

    @Test
    fun `cannot approve a specialist cell type change request when a prisoner is in the cell`() {
      val cell = firstLeedsCell()
      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.DRY),
        workingCapacity = 0,
        maxCapacity = 0,
        certifiedNormalAccommodation = 0,
      )

      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(cell.getPathHierarchy()),
        true,
      )

      assertThat(
        webTestClient.put().uri("/certification/location/approve")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingApproval.id)))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)
          .expectBody<ErrorResponse>()
          .returnResult().responseBody!!.errorCode,
      ).isEqualTo(ErrorCode.MaxCapacityCannotBeBelowOccupancyLevel.errorCode)
    }

    @Test
    fun `can approve a specialist cell type change request`() {
      val cell = firstLeedsCell()
      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.DRY),
        workingCapacity = 0,
        maxCapacity = 1,
        certifiedNormalAccommodation = 0,
      )

      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(cell.getPathHierarchy()),
        false,
      )

      val approved = approveRequest(pendingApproval.id)

      assertThat(approved.approvalType).isEqualTo(ApprovalType.SPECIALIST_CELL_TYPE)
      assertThat(approved.status).isEqualTo(ApprovalRequestStatus.APPROVED)

      getDomainEvents(3).let {
        assertThat(it.map { msg -> msg.eventType to msg.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to cell.getKey(),
          *cell.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }

      webTestClient.get().uri("/cell-certificates/${approved.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].specialistCellTypes").isEqualTo(listOf("DRY"))
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].workingCapacity").isEqualTo(0)
        .jsonPath("$.locations[0].subLocations[0].subLocations[0].maxCapacity").isEqualTo(1)

      // Verify the cell has been updated
      val updatedCell = webTestClient.get().uri("/locations/${cell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(updatedCell.specialistCellTypes).containsExactlyInAnyOrder(SpecialistCellType.DRY)
      assertThat(updatedCell.capacity?.workingCapacity).isEqualTo(0)
      assertThat(updatedCell.capacity?.maxCapacity).isEqualTo(1)
      assertThat(updatedCell.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(updatedCell.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `can reject a specialist cell type change request`() {
      val cell = firstLeedsCell()
      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.CONSTANT_SUPERVISION),
        workingCapacity = 0,
        maxCapacity = 1,
        certifiedNormalAccommodation = 0,
      )

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingApproval.id, comments = "Not approved")))
        .exchange()
        .expectStatus().isOk

      // Cell should be unchanged (no specialist types, no capacity change)
      val cell2 = webTestClient.get().uri("/locations/${cell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(cell2.specialistCellTypes).isNullOrEmpty()
      assertThat(cell2.capacity?.workingCapacity).isEqualTo(1)
      assertThat(cell2.capacity?.maxCapacity).isEqualTo(2)
      assertThat(cell2.pendingApprovalRequestId).isNull()

      val currentCertAfterReject = webTestClient.get().uri("/cell-certificates/prison/${cell.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterReject = currentCertAfterReject.findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCertAfterReject).isNotNull
      assertThat(cellInCertAfterReject?.specialistCellTypes).isNullOrEmpty()
      assertThat(cellInCertAfterReject?.workingCapacity).isEqualTo(1)
      assertThat(cellInCertAfterReject?.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `can withdraw a specialist cell type change request`() {
      val cell = firstLeedsCell()
      val pendingApproval = requestSpecialistCellTypeApproval(
        cell = cell,
        specialistCellTypes = setOf(SpecialistCellType.UNFURNISHED),
        workingCapacity = 0,
        maxCapacity = 1,
        certifiedNormalAccommodation = 0,
      )

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingApproval.id, comments = "No longer needed")))
        .exchange()
        .expectStatus().isOk

      val unlockedCell = webTestClient.get().uri("/locations/${cell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(unlockedCell.specialistCellTypes).isNullOrEmpty()
      assertThat(unlockedCell.pendingApprovalRequestId).isNull()

      val currentCertAfterWithdraw = webTestClient.get().uri("/cell-certificates/prison/${cell.prisonId}/current")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CellCertificateDto>()
        .returnResult().responseBody!!

      val cellInCertAfterWithdraw = currentCertAfterWithdraw.findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCertAfterWithdraw).isNotNull
      assertThat(cellInCertAfterWithdraw?.specialistCellTypes).isNullOrEmpty()
      assertThat(cellInCertAfterWithdraw?.workingCapacity).isEqualTo(1)
      assertThat(cellInCertAfterWithdraw?.maxCapacity).isEqualTo(2)
    }

    @Test
    fun `cannot create a second specialist cell type approval when one is pending`() {
      val cell = firstLeedsCell()
      requestSpecialistCellTypeApproval(cell, setOf(SpecialistCellType.CSU))

      webTestClient.put().uri("/certification/location/specialist-cell-type-change")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            SpecialistCellTypeApprovalRequest(
              locationId = cell.id!!,
              specialistCellTypes = setOf(SpecialistCellType.DRY),
              workingCapacity = 0,
              maxCapacity = 1,
              certifiedNormalAccommodation = 0,
            ),
          ),
        )
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `approval endpoint rejects change that does not require approval (same capacity-affecting count)`() {
      val cell = firstLeedsCell()

      // First add CSU via approval
      val pending = requestSpecialistCellTypeApproval(cell, setOf(SpecialistCellType.CSU))

      prisonerSearchMockServer.stubSearchByLocations(
        leedsWing.prisonId,
        listOf(cell.getPathHierarchy()),
        false,
      )

      approveRequest(pending.id)
      getDomainEvents(3)

      // Now try to use the approval endpoint to swap CSU → BIOHAZARD (count stays at 1, doesn't need approval)
      webTestClient.put().uri("/certification/location/specialist-cell-type-change")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            SpecialistCellTypeApprovalRequest(
              locationId = cell.id!!,
              specialistCellTypes = setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST),
              workingCapacity = 0,
              maxCapacity = 1,
              certifiedNormalAccommodation = 0,
            ),
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `cert approval not required, when adding a non specialist cell type`() {
      val cell = firstLeedsCell()
      webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.ACCESSIBLE_CELL, SpecialistCellType.CAT_A)))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!
        .also { result ->
          assertThat(result.specialistCellTypes).containsExactlyInAnyOrder(
            SpecialistCellType.ACCESSIBLE_CELL,
            SpecialistCellType.CAT_A,
          )
        }

      getDomainEvents(1)
    }

    @Test
    fun `when cert approval not required, adding capacity-affecting specialist cell types via direct endpoint is allowed`() {
      // cell1 is in MDI which does not have certificationApprovalRequired=true
      webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST, SpecialistCellType.ACCESSIBLE_CELL)))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!
        .also { result ->
          assertThat(result.specialistCellTypes).containsExactlyInAnyOrder(
            SpecialistCellType.BIOHAZARD_DIRTY_PROTEST,
            SpecialistCellType.ACCESSIBLE_CELL,
          )
        }

      getDomainEvents(1)
    }
  }

  @DisplayName("PUT /certification/prison/signed-op-cap-change")
  @Nested
  inner class SignedOpCapApprovalTest {

    private fun requestSignedOpCapChange(newCapacity: Int = 10): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/prison/signed-op-cap-change")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          SignedOpCapApprovalRequest(
            prisonId = "LEI",
            signedOperationalCapacity = newCapacity,
            reasonForChange = "Prison capacity has increased",
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun getSignedOpCap(): Int = webTestClient.get().uri("/signed-op-cap/LEI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody<SignedOperationCapacityDto>()
      .returnResult().responseBody!!.signedOperationCapacity

    @Test
    fun `can request a signed op cap change approval`() {
      val pendingApproval = requestSignedOpCapChange(newCapacity = 10)

      assertThat(pendingApproval.approvalType).isEqualTo(ApprovalType.SIGNED_OP_CAP)
      assertThat(pendingApproval.status).isEqualTo(ApprovalRequestStatus.PENDING)
      assertThat(pendingApproval.prisonId).isEqualTo("LEI")
      assertThat(pendingApproval.currentSignedOperationCapacity).isEqualTo(12)
      assertThat(pendingApproval.signedOperationCapacityChange).isEqualTo(-2)
      assertThat(getSignedOpCap()).isEqualTo(12)
    }

    @Test
    fun `can approve a signed op cap change`() {
      val pendingApproval = requestSignedOpCapChange(newCapacity = 10)

      val approvedRequest = webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = pendingApproval.id,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(approvedRequest.status).isEqualTo(ApprovalRequestStatus.APPROVED)
      assertThat(approvedRequest.approvalType).isEqualTo(ApprovalType.SIGNED_OP_CAP)
      assertThat(getSignedOpCap()).isEqualTo(10)
    }

    @Test
    fun `can reject a signed op cap change`() {
      val pendingApproval = requestSignedOpCapChange(newCapacity = 10)

      val rejectedRequest = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = pendingApproval.id,
              comments = "Capacity increase not approved",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejectedRequest.certificateId).isNull()
      assertThat(rejectedRequest.status).isEqualTo(ApprovalRequestStatus.REJECTED)
      assertThat(getSignedOpCap()).isEqualTo(12)
    }

    @Test
    fun `can withdraw a signed op cap change`() {
      val pendingApproval = requestSignedOpCapChange(newCapacity = 10)

      val withdrawnRequest = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = pendingApproval.id,
              comments = "Capacity change request withdrawn",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(withdrawnRequest.certificateId).isNull()
      assertThat(withdrawnRequest.status).isEqualTo(ApprovalRequestStatus.WITHDRAWN)
      assertThat(getSignedOpCap()).isEqualTo(12)
    }
  }

  @DisplayName("Decision endpoint error scenarios")
  @Nested
  inner class DecisionErrorScenariosTest {

    private fun createPendingCapacityChangeRequest(): UUID {
      leedsWing.findAllLeafLocations().forEach { cell ->
        prisonerSearchMockServer.stubSearchByLocations(
          leedsWing.prisonId,
          listOf(cell.getPathHierarchy()),
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
              temporaryWorkingCapacityChange = false,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk

      return webTestClient.get().uri("/locations/${firstCell.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!.pendingApprovalRequestId!!
    }

    @Test
    fun `approve returns 404 for a non-existent approval request`() {
      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = UUID.randomUUID())))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotFound.errorCode)
    }

    @Test
    fun `reject returns 404 for a non-existent approval request`() {
      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = UUID.randomUUID(), comments = "test")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotFound.errorCode)
    }

    @Test
    fun `withdraw returns 404 for a non-existent approval request`() {
      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = UUID.randomUUID(), comments = "test")))
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotFound.errorCode)
    }

    @Test
    fun `approve returns 400 when the approval request is already approved`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(3)

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `approve returns 400 when the approval request is already rejected`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingId, comments = "rejected")))
        .exchange()
        .expectStatus().isOk

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `approve returns 400 when the approval request is already withdrawn`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingId, comments = "withdrawn")))
        .exchange()
        .expectStatus().isOk

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `reject returns 400 when the approval request is already rejected`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingId, comments = "rejected")))
        .exchange()
        .expectStatus().isOk

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingId, comments = "rejected again")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `reject returns 400 when the approval request is already approved`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(3)

      webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = pendingId, comments = "rejected")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `withdraw returns 400 when the approval request is already withdrawn`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingId, comments = "withdrawn")))
        .exchange()
        .expectStatus().isOk

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingId, comments = "withdrawn again")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }

    @Test
    fun `withdraw returns 400 when the approval request is already approved`() {
      val pendingId = createPendingCapacityChangeRequest()

      webTestClient.put().uri("/certification/location/approve")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = pendingId)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(3)

      webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = pendingId, comments = "withdrawn")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(ErrorCode.ApprovalRequestNotInPendingStatus.errorCode)
    }
  }

  @DisplayName("PUT /locations/{id}/convert-to-cell (certification approval)")
  @Nested
  inner class ConvertRoomToCellTest {

    private fun firstLeedsCell(): Cell = leedsWing.findAllLeafLocations().first() as Cell

    private fun getApproval(id: UUID): CertificationApprovalRequestDto = webTestClient.get().uri("/certification/request-approvals/$id")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun approveRequest(id: UUID): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = id)))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun getLocation(id: UUID): Location = webTestClient.get().uri("/locations/$id?includeCurrentCertificate=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!

    private fun currentCertificate(prisonId: String): CellCertificateDto = webTestClient.get().uri("/cell-certificates/prison/$prisonId/current")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .exchange()
      .expectStatus().isOk
      .expectBody<CellCertificateDto>()
      .returnResult().responseBody!!

    private fun setCertificationApprovalRequired(prisonId: String, status: String) {
      webTestClient.put().uri("/prison-configuration/$prisonId/certification-approval-required/$status")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
        .exchange()
        .expectStatus().isOk
    }

    /**
     * Produces a converted (non-residential) room on the certification-approval prison (LEI). Converting a cell to a
     * room is blocked while certification approval is active, so the prison's approval flag is temporarily turned off
     * and the cell is converted directly. The existing certificate is then dropped before the flag is turned back on
     * (which triggers a baseline): a baseline clones the cell's previous certificate entry for locations it already
     * holds, so without clearing it first the regenerated certificate would still show the cell as residential.
     * Starting from no certificate forces a full rebuild that reflects the cell as a converted room.
     *
     * Drains the domain events the direct conversion produces (LOCATION_AMENDED for the cell and each of its
     * parents); the flag toggles and the baseline publish no domain events.
     */
    private fun convertedRoomOnLeeds(): Cell {
      val cell = firstLeedsCell()
      prisonerSearchMockServer.stubSearchByLocations(cell.prisonId, listOf(cell.getPathHierarchy()), false)

      setCertificationApprovalRequired(cell.prisonId, "INACTIVE")
      webTestClient.put().uri("/locations/${cell.id}/convert-cell-to-non-res-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(convertedCellType = ConvertedCellType.OFFICE)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(cell.getParentLocations().size + 1)
      cellCertificateRepository.deleteAll()
      setCertificationApprovalRequired(cell.prisonId, "ACTIVE")
      return cell
    }

    private fun requestConvertToCell(
      cell: Cell,
      accommodationType: LocationResidentialResource.AllowedAccommodationTypeForConversion = LocationResidentialResource.AllowedAccommodationTypeForConversion.NORMAL_ACCOMMODATION,
      maxCapacity: Int = 3,
      workingCapacity: Int = 2,
      certifiedNormalAccommodation: Int = 2,
      specialistCellTypes: Set<SpecialistCellType>? = setOf(SpecialistCellType.ACCESSIBLE_CELL),
      usedForTypes: List<UsedForType>? = listOf(UsedForType.STANDARD_ACCOMMODATION),
      cellMark: String? = null,
      inCellSanitation: Boolean? = null,
    ): Location = webTestClient.put().uri("/locations/${cell.id}/convert-to-cell")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          LocationResidentialResource.ConvertToCellRequest(
            accommodationType = accommodationType,
            specialistCellTypes = specialistCellTypes,
            certifiedNormalAccommodation = certifiedNormalAccommodation,
            maxCapacity = maxCapacity,
            workingCapacity = workingCapacity,
            usedForTypes = usedForTypes,
            cellMark = cellMark,
            inCellSanitation = inCellSanitation,
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!

    @Test
    fun `requesting a convert-to-cell leaves the room unchanged but locked and captures the new values`() {
      val cell = convertedRoomOnLeeds()

      val updatedLocation = requestConvertToCell(cell)

      // The location is still a non-residential room, but locked pending approval (NOT yet converted).
      assertThat(updatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_NON_RESIDENTIAL)
      assertThat(updatedLocation.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(updatedLocation.pendingApprovalRequestId).isNotNull

      // No domain event is published when the approval is merely requested.
      assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(0)

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.approvalType).isEqualTo(ApprovalType.CONVERT_ROOM_TO_CELL)
      assertThat(approval.status).isEqualTo(ApprovalRequestStatus.PENDING)
      assertThat(approval.locationId).isEqualTo(cell.id)
      assertThat(approval.locationKey).isEqualTo(cell.getKey())

      // The pending approval holds the new cell values.
      assertThat(approval.workingCapacity).isEqualTo(2)
      assertThat(approval.maxCapacity).isEqualTo(3)
      assertThat(approval.certifiedNormalAccommodation).isEqualTo(2)
      assertThat(approval.specialistCellTypes).containsExactly(SpecialistCellType.ACCESSIBLE_CELL)
      assertThat(approval.accommodationType).isEqualTo(AccommodationType.NORMAL_ACCOMMODATION)
      assertThat(approval.usedForTypes).containsExactly(UsedForType.STANDARD_ACCOMMODATION)

      // The current converted cell type being removed is captured so the UI can play back the change.
      assertThat(approval.currentConvertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(approval.currentOtherConvertedCellType).isNull()

      // The proposed accommodation type / used-for match the parent's current values, so the current
      // accommodation / used-for are not surfaced (nothing notable changes relative to the parent).
      assertThat(approval.currentAccommodationTypes).isNull()
      assertThat(approval.currentUsedForTypes).isNull()

      // A converted room has no current capacity, so the change is "None -> new".
      assertThat(approval.locations!![0].currentWorkingCapacity).isEqualTo(0)
      assertThat(approval.locations!![0].currentMaxCapacity).isEqualTo(0)
      assertThat(approval.workingCapacityChange).isEqualTo(2)
      assertThat(approval.maxCapacityChange).isEqualTo(3)
      assertThat(approval.certifiedNormalAccommodationChange).isEqualTo(2)
    }

    @Test
    fun `convert-to-cell surfaces the parent accommodation type when the proposed type differs`() {
      val cell = convertedRoomOnLeeds()

      // The parent's cells are all NORMAL_ACCOMMODATION, so proposing CARE_AND_SEPARATION differs.
      val updatedLocation = requestConvertToCell(
        cell,
        accommodationType = LocationResidentialResource.AllowedAccommodationTypeForConversion.CARE_AND_SEPARATION,
        usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION),
      )

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.accommodationType).isEqualTo(AccommodationType.CARE_AND_SEPARATION)
      // The parent's current accommodation type is surfaced because the proposed type is not held by it.
      assertThat(approval.currentAccommodationTypes).containsExactly(AccommodationType.NORMAL_ACCOMMODATION)
      // Used-for still matches the parent, so it is not surfaced.
      assertThat(approval.currentUsedForTypes).isNull()
    }

    @Test
    fun `convert-to-cell surfaces the parent used-for when the proposed used-for differs`() {
      val cell = convertedRoomOnLeeds()

      // The parent's cells are used for STANDARD_ACCOMMODATION only, so proposing an extra used-for differs.
      val updatedLocation = requestConvertToCell(
        cell,
        usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.FIRST_NIGHT_CENTRE),
      )

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      // Accommodation type still matches the parent, so it is not surfaced.
      assertThat(approval.currentAccommodationTypes).isNull()
      // The parent's current used-for is surfaced because a proposed used-for is not held by it.
      assertThat(approval.currentUsedForTypes).containsExactly(UsedForType.STANDARD_ACCOMMODATION)
    }

    @Test
    fun `convert-to-cell captures the current OTHER converted cell type and free text`() {
      val cell = firstLeedsCell()
      prisonerSearchMockServer.stubSearchByLocations(cell.prisonId, listOf(cell.getPathHierarchy()), false)

      // Convert the cell to an OTHER non-residential room (with free text) directly while approval is off.
      setCertificationApprovalRequired(cell.prisonId, "INACTIVE")
      webTestClient.put().uri("/locations/${cell.id}/convert-cell-to-non-res-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(
              convertedCellType = ConvertedCellType.OTHER,
              otherConvertedCellType = "Swimming pool",
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
      getDomainEvents(cell.getParentLocations().size + 1)
      cellCertificateRepository.deleteAll()
      setCertificationApprovalRequired(cell.prisonId, "ACTIVE")

      val updatedLocation = requestConvertToCell(cell)

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.currentConvertedCellType).isEqualTo(ConvertedCellType.OTHER)
      assertThat(approval.currentOtherConvertedCellType).isEqualTo("Swimming pool")
    }

    @Test
    fun `approving a convert-to-cell makes it an active cell again and updates the certificate`() {
      val cell = convertedRoomOnLeeds()
      val updatedLocation = requestConvertToCell(cell)

      val approved = approveRequest(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approved.approvalType).isEqualTo(ApprovalType.CONVERT_ROOM_TO_CELL)
      assertThat(approved.status).isEqualTo(ApprovalRequestStatus.APPROVED)
      assertThat(approved.certificateId).isNotNull

      getDomainEvents(3).let {
        assertThat(it.map { msg -> msg.eventType to msg.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to cell.getKey(),
          *cell.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }

      // The room is now an active cell again with the requested capacity and specialist types.
      val convertedCell = getLocation(cell.id!!)
      assertThat(convertedCell.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(convertedCell.convertedCellType).isNull()
      assertThat(convertedCell.pendingApprovalRequestId).isNull()
      assertThat(convertedCell.capacity?.workingCapacity).isEqualTo(2)
      assertThat(convertedCell.capacity?.maxCapacity).isEqualTo(3)
      assertThat(convertedCell.capacity?.certifiedNormalAccommodation).isEqualTo(2)
      assertThat(convertedCell.specialistCellTypes).containsExactly(SpecialistCellType.ACCESSIBLE_CELL)

      // The new certificate includes the re-certified cell (baseline 5/10/5 + the cell 2/3/2).
      webTestClient.get().uri("/cell-certificates/${approved.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(7)
        .jsonPath("$.totalMaxCapacity").isEqualTo(13)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(7)

      val cellInCert = currentCertificate(cell.prisonId).findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCert!!.workingCapacity).isEqualTo(2)
    }

    @Test
    fun `requesting a convert-to-cell captures the proposed and current cell mark and sanitation`() {
      val cell = convertedRoomOnLeeds()
      val room = getLocation(cell.id!!)

      val updatedLocation = requestConvertToCell(cell, cellMark = "A-NEW", inCellSanitation = true)

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      // The proposed door number / sanitation for the re-created cell.
      assertThat(approval.cellMark).isEqualTo("A-NEW")
      assertThat(approval.inCellSanitation).isTrue()
      // The room's existing values are snapshotted so the UI can play back "current -> new".
      assertThat(approval.currentCellMark).isEqualTo(room.cellMark)
      assertThat(approval.currentInCellSanitation).isEqualTo(room.inCellSanitation)
    }

    @Test
    fun `convert-to-cell without cell mark or sanitation leaves those approval values unset`() {
      val cell = convertedRoomOnLeeds()

      val updatedLocation = requestConvertToCell(cell)

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.cellMark).isNull()
      assertThat(approval.currentCellMark).isNull()
      assertThat(approval.inCellSanitation).isNull()
      assertThat(approval.currentInCellSanitation).isNull()
    }

    @Test
    fun `approving a convert-to-cell applies the requested cell mark and sanitation to the cell and certificate`() {
      val cell = convertedRoomOnLeeds()
      val updatedLocation = requestConvertToCell(cell, cellMark = "A-NEW", inCellSanitation = true)

      val approved = approveRequest(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approved.status).isEqualTo(ApprovalRequestStatus.APPROVED)
      getDomainEvents(3)

      // The re-created cell carries the requested door number / sanitation.
      val convertedCell = getLocation(cell.id!!)
      assertThat(convertedCell.cellMark).isEqualTo("A-NEW")
      assertThat(convertedCell.inCellSanitation).isTrue()

      // And so does the new certificate entry for the cell.
      val cellInCert = currentCertificate(cell.prisonId).findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCert!!.cellMark).isEqualTo("A-NEW")
      assertThat(cellInCert.inCellSanitation).isTrue()
    }

    @Test
    fun `rejecting a convert-to-cell leaves the location as a non-residential room`() {
      val cell = convertedRoomOnLeeds()
      val updatedLocation = requestConvertToCell(cell)

      val rejected = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = updatedLocation.pendingApprovalRequestId!!, comments = "Not approved")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejected.status).isEqualTo(ApprovalRequestStatus.REJECTED)
      assertThat(rejected.certificateId).isNull()

      // The location remains a converted non-residential room, NOT converted to a cell.
      val rejectedRoom = getLocation(cell.id!!)
      assertThat(rejectedRoom.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
      assertThat(rejectedRoom.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(rejectedRoom.pendingApprovalRequestId).isNull()

      // Certificate unchanged: the location is still listed as a converted room with zero capacity.
      val roomInCert = currentCertificate(cell.prisonId).findLocationInCertificate(cell.getPathHierarchy())
      assertThat(roomInCert!!.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(roomInCert.workingCapacity).isEqualTo(0)
    }

    @Test
    fun `withdrawing a convert-to-cell leaves the location as a non-residential room`() {
      val cell = convertedRoomOnLeeds()
      val updatedLocation = requestConvertToCell(cell)

      val withdrawn = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = updatedLocation.pendingApprovalRequestId!!, comments = "Changed my mind")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(withdrawn.status).isEqualTo(ApprovalRequestStatus.WITHDRAWN)

      val withdrawnRoom = getLocation(cell.id!!)
      assertThat(withdrawnRoom.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
      assertThat(withdrawnRoom.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(withdrawnRoom.pendingApprovalRequestId).isNull()
    }

    @Test
    fun `invalid capacity is rejected at request time and raises no approval`() {
      val cell = convertedRoomOnLeeds()

      webTestClient.put().uri("/locations/${cell.id}/convert-to-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            LocationResidentialResource.ConvertToCellRequest(
              accommodationType = LocationResidentialResource.AllowedAccommodationTypeForConversion.NORMAL_ACCOMMODATION,
              certifiedNormalAccommodation = 0,
              maxCapacity = 0,
              workingCapacity = 0,
            ),
          ),
        )
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_REQUEST)

      // No approval was raised; the location is still a non-residential room.
      assertThat(getLocation(cell.id!!).status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
    }

    @Test
    fun `on a prison that does not require certification approval the room is converted directly`() {
      // Convert cell1 (MDI - no certification approval) to a room, then back to a cell, both directly.
      prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)
      webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(convertedCellType = ConvertedCellType.STORE)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(3)

      val result = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            LocationResidentialResource.ConvertToCellRequest(
              accommodationType = LocationResidentialResource.AllowedAccommodationTypeForConversion.NORMAL_ACCOMMODATION,
              certifiedNormalAccommodation = 1,
              maxCapacity = 2,
              workingCapacity = 1,
            ),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(result.status).isEqualTo(DerivedLocationStatus.ACTIVE)
      assertThat(result.convertedCellType).isNull()
      assertThat(result.pendingApprovalRequestId).isNull()

      getDomainEvents(3).let { messages ->
        assertThat(messages.map { it.eventType to it.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to cell1.getKey(),
          *cell1.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/convert-cell-to-non-res-cell (with certification approval)")
  @Nested
  inner class ConvertCellToNonResidentialRoomTest {

    private fun firstLeedsCell(): Cell = leedsWing.findAllLeafLocations().first() as Cell

    private fun stubNoPrisoners(cell: Cell) {
      prisonerSearchMockServer.stubSearchByLocations(cell.prisonId, listOf(cell.getPathHierarchy()), false)
    }

    private fun requestConversion(
      cell: Cell,
      convertedCellType: ConvertedCellType = ConvertedCellType.OFFICE,
    ): Location = webTestClient.put().uri("/locations/${cell.id}/convert-cell-to-non-res-cell")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(convertedCellType = convertedCellType)))
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!

    private fun getApproval(id: UUID): CertificationApprovalRequestDto = webTestClient.get().uri("/certification/request-approvals/$id")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun approveRequest(id: UUID): CertificationApprovalRequestDto = webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(ApproveCertificationRequestDto(approvalRequestReference = id)))
      .exchange()
      .expectStatus().isOk
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!

    private fun getLocation(id: UUID): Location = webTestClient.get().uri("/locations/$id?includeCurrentCertificate=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
      .exchange()
      .expectStatus().isOk
      .expectBody<Location>()
      .returnResult().responseBody!!

    private fun currentCertificate(prisonId: String): CellCertificateDto = webTestClient.get().uri("/cell-certificates/prison/$prisonId/current")
      .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .exchange()
      .expectStatus().isOk
      .expectBody<CellCertificateDto>()
      .returnResult().responseBody!!

    @Test
    fun `requesting a conversion temporarily deactivates the cell and captures current values as new=None`() {
      val cell = firstLeedsCell()
      stubNoPrisoners(cell)

      val updatedLocation = requestConversion(cell)

      // Cell is now temporarily inactive (off cert) with a pending approval.
      assertThat(updatedLocation.status).isEqualTo(DerivedLocationStatus.LOCKED_INACTIVE)
      assertThat(updatedLocation.deactivatedReason).isEqualTo(DeactivatedReason.CONVERT_CELL_TO_ROOM)
      assertThat(updatedLocation.inactiveStatus).isEqualTo(InactiveStatus.INACTIVE_PEND_CHANGE_REQ)
      assertThat(updatedLocation.pendingApprovalRequestId).isNotNull
      // Not yet converted
      assertThat(updatedLocation.convertedCellType).isNull()

      // The cell is deactivated; its parents are amended.
      getDomainEvents(3).let { messages ->
        assertThat(messages.map { it.eventType to it.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.deactivated" to cell.getKey(),
          *cell.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.approvalType).isEqualTo(ApprovalType.CONVERT_CELL_TO_ROOM)
      assertThat(approval.status).isEqualTo(ApprovalRequestStatus.PENDING)
      assertThat(approval.locationId).isEqualTo(cell.id)
      assertThat(approval.locationKey).isEqualTo(cell.getKey())

      // The new (proposed) value is the converted cell type...
      assertThat(approval.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(approval.otherConvertedCellType).isNull()

      // ...everything else is being removed, so the new (pending) values are null => UI shows "current -> None".
      assertThat(approval.workingCapacity).isNull()
      assertThat(approval.maxCapacity).isNull()
      assertThat(approval.certifiedNormalAccommodation).isNull()
      assertThat(approval.specialistCellTypes).isNull()
      assertThat(approval.inCellSanitation).isNull()

      // Current values are surfaced.
      assertThat(approval.currentInCellSanitation).isTrue()
      assertThat(approval.workingCapacityChange).isEqualTo(-1)
      assertThat(approval.locations!![0].currentWorkingCapacity).isEqualTo(1)
      assertThat(approval.locations!![0].currentMaxCapacity).isEqualTo(2)
      assertThat(approval.locations!![0].currentCertifiedNormalAccommodation).isEqualTo(1)
    }

    @Test
    fun `requesting a conversion captures current specialist cell types`() {
      val cell = firstLeedsCell()

      // Add a non-capacity-affecting specialist type directly (allowed without approval), updating the cert in place.
      webTestClient.put().uri("/locations/${cell.id}/specialist-cell-types")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(setOf(SpecialistCellType.ACCESSIBLE_CELL)))
        .exchange()
        .expectStatus().isOk
      getDomainEvents(1)

      stubNoPrisoners(cell)
      val updatedLocation = requestConversion(cell)
      getDomainEvents(3)

      val approval = getApproval(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approval.currentSpecialistCellTypes).containsExactly(SpecialistCellType.ACCESSIBLE_CELL)
      // New specialist cell types are removed -> None
      assertThat(approval.specialistCellTypes).isNull()
    }

    @Test
    fun `cannot request a conversion when a prisoner is in the cell`() {
      val cell = firstLeedsCell()
      prisonerSearchMockServer.stubSearchByLocations(cell.prisonId, listOf(cell.getPathHierarchy()), true)

      assertThat(
        webTestClient.put().uri("/locations/${cell.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(convertedCellType = ConvertedCellType.OFFICE)))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
          .expectBody<ErrorResponse>()
          .returnResult().responseBody!!.errorCode,
      ).isEqualTo(ErrorCode.DeactivationErrorLocationsContainPrisoners.errorCode)

      // No approval raised and the cell remains active.
      assertThat(getLocation(cell.id!!).status).isEqualTo(DerivedLocationStatus.ACTIVE)
    }

    @Test
    fun `approving a conversion converts the cell to an active non-residential room and updates the certificate`() {
      val cell = firstLeedsCell()
      stubNoPrisoners(cell)
      val updatedLocation = requestConversion(cell)
      getDomainEvents(3)

      val approved = approveRequest(updatedLocation.pendingApprovalRequestId!!)
      assertThat(approved.approvalType).isEqualTo(ApprovalType.CONVERT_CELL_TO_ROOM)
      assertThat(approved.status).isEqualTo(ApprovalRequestStatus.APPROVED)
      assertThat(approved.certificateId).isNotNull

      getDomainEvents(3).let {
        assertThat(it.map { msg -> msg.eventType to msg.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to cell.getKey(),
          *cell.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }

      // The cell is now an active non-residential converted room with no capacity or specialist types.
      val convertedCell = getLocation(cell.id!!)
      assertThat(convertedCell.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
      assertThat(convertedCell.convertedCellType).isEqualTo(ConvertedCellType.OFFICE)
      assertThat(convertedCell.pendingApprovalRequestId).isNull()
      assertThat(convertedCell.deactivatedReason).isNull()
      // Capacity has been removed (a converted room reports zero capacity).
      assertThat(convertedCell.capacity?.workingCapacity ?: 0).isEqualTo(0)
      assertThat(convertedCell.capacity?.maxCapacity ?: 0).isEqualTo(0)
      assertThat(convertedCell.specialistCellTypes).isNullOrEmpty()

      // The new certificate no longer counts the converted cell (was wc1/mc2/cna1 of the 6/12/6 baseline).
      webTestClient.get().uri("/cell-certificates/${approved.certificateId}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(5)
        .jsonPath("$.totalMaxCapacity").isEqualTo(10)
        .jsonPath("$.totalCertifiedNormalAccommodation").isEqualTo(5)
    }

    @Test
    fun `rejecting a conversion leaves the cell temporarily inactive and the certificate unchanged`() {
      val cell = firstLeedsCell()
      stubNoPrisoners(cell)
      val updatedLocation = requestConversion(cell)
      getDomainEvents(3)

      val rejected = webTestClient.put().uri("/certification/location/reject")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(RejectCertificationRequestDto(approvalRequestReference = updatedLocation.pendingApprovalRequestId!!, comments = "Not approved")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(rejected.status).isEqualTo(ApprovalRequestStatus.REJECTED)
      assertThat(rejected.certificateId).isNull()

      // Cell remains temporarily inactive with the conversion reason, and is NOT converted.
      val rejectedCell = getLocation(cell.id!!)
      assertThat(rejectedCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(rejectedCell.deactivatedReason).isEqualTo(DeactivatedReason.CONVERT_CELL_TO_ROOM)
      assertThat(rejectedCell.pendingApprovalRequestId).isNull()
      assertThat(rejectedCell.convertedCellType).isNull()

      // Certificate still has the cell with its original working capacity.
      val cellInCert = currentCertificate(cell.prisonId).findLocationInCertificate(cell.getPathHierarchy())
      assertThat(cellInCert!!.workingCapacity).isEqualTo(1)
    }

    @Test
    fun `withdrawing a conversion leaves the cell temporarily inactive`() {
      val cell = firstLeedsCell()
      stubNoPrisoners(cell)
      val updatedLocation = requestConversion(cell)
      getDomainEvents(3)

      val withdrawn = webTestClient.put().uri("/certification/location/withdraw")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(WithdrawCertificationRequestDto(approvalRequestReference = updatedLocation.pendingApprovalRequestId!!, comments = "Changed my mind")))
        .exchange()
        .expectStatus().isOk
        .expectBody<CertificationApprovalRequestDto>()
        .returnResult().responseBody!!

      assertThat(withdrawn.status).isEqualTo(ApprovalRequestStatus.WITHDRAWN)
      assertThat(withdrawn.certificateId).isNull()

      val withdrawnCell = getLocation(cell.id!!)
      assertThat(withdrawnCell.status).isEqualTo(DerivedLocationStatus.INACTIVE)
      assertThat(withdrawnCell.deactivatedReason).isEqualTo(DeactivatedReason.CONVERT_CELL_TO_ROOM)
      assertThat(withdrawnCell.pendingApprovalRequestId).isNull()
      assertThat(withdrawnCell.convertedCellType).isNull()
    }

    @Test
    fun `on a prison that does not require certification approval the cell is converted directly`() {
      // cell1 is in MDI which does not require certification approval.
      prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

      val result = webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(jsonString(LocationResidentialResource.ConvertCellToNonResidentialLocationRequest(convertedCellType = ConvertedCellType.STORE)))
        .exchange()
        .expectStatus().isOk
        .expectBody<Location>()
        .returnResult().responseBody!!

      assertThat(result.status).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
      assertThat(result.convertedCellType).isEqualTo(ConvertedCellType.STORE)
      assertThat(result.pendingApprovalRequestId).isNull()

      getDomainEvents(3).let { messages ->
        assertThat(messages.map { it.eventType to it.additionalInformation?.key }).containsExactlyInAnyOrder(
          "location.inside.prison.amended" to cell1.getKey(),
          *cell1.getParentLocations().map { parent -> "location.inside.prison.amended" to parent.getKey() }.toTypedArray(),
        )
      }
    }
  }
}
