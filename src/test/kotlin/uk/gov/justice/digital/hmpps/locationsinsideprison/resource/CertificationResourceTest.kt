package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Certification Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationResourceTest : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID
  private lateinit var mWing: ResidentialLocation

  @Autowired
  lateinit var cellCertificateRepository: CellCertificateRepository

  @BeforeEach
  override fun setUp() {
    cellCertificateRepository.deleteAll()
    super.setUp()
    approvalRequestId = getApprovalRequestId()

    // Create a new wing in Leeds prison
    mWing = repository.saveAndFlush(
      CreateEntireWingRequest(
        prisonId = "LEI",
        wingCode = "M",
        numberOfCellsPerSection = 3,
        numberOfLandings = 2,
        numberOfSpurs = 0,
        defaultWorkingCapacity = 1,
        defaultMaxCapacity = 2,
        defaultCNA = 1,
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
  }

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
      fun `cannot request approval for a location which is not DRAFT nor has any pending changes`() {
        val aCell = leedsWing.cellLocations().find { it.getKey() == "LEI-A-1-002" } ?: throw RuntimeException("Cell not found")

        Assertions.assertThat(
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
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.LocationDoesNotRequireApproval.errorCode)
      }

      @Test
      fun `cannot request approval for a location which has already been sent for approval`() {
        val aCell = repository.findOneByKey("LEI-A-1-001") as Cell

        assertThat(
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
            .expectStatus().isEqualTo(409)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ApprovalRequestAlreadyExists.errorCode)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can request approval for a location that has pending changes`() {
        val aCell = repository.findOneByKey("LEI-A-1-002") as Cell
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
              "status": "PENDING",
              "maxCapacityChange": 1,
              "workingCapacityChange": 0,
              "certifiedNormalAccommodationChange": 0,
              "locations": [
                {
                  "locationCode": "002",
                  "cellMark": "A-2",
                  "localName": "Cell 2 On 1",
                  "pathHierarchy": "A-1-002",
                  "level": 3,
                  "certifiedNormalAccommodation": 1,
                  "workingCapacity": 1,
                  "maxCapacity": 3,
                  "inCellSanitation": true,
                  "locationType": "CELL"
                }
              ]
              }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can request approval for a location that is a draft location`() {
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
              "status": "PENDING",
              "maxCapacityChange": 12,
              "workingCapacityChange": 6,
              "certifiedNormalAccommodationChange": 6,
              "locations": [
              {
                "pathHierarchy": "M",
                "level": 1,
                "certifiedNormalAccommodation": 6,
                "workingCapacity": 6,
                "maxCapacity": 12,
                "locationType": "WING",
                "subLocations": [
                  {
                    "pathHierarchy": "M-1",
                    "level": 2,
                    "certifiedNormalAccommodation": 3,
                    "workingCapacity": 3,
                    "maxCapacity": 6,
                    "locationType": "LANDING",
                    "subLocations": [
                      {
                        "cellMark": "M-1",
                        "pathHierarchy": "M-1-001",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      },
                      {
                        "cellMark": "M-2",
                        "pathHierarchy": "M-1-002",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      },
                      {
                        "cellMark": "M-3",
                        "pathHierarchy": "M-1-003",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      }
                    ]
                  },
                  {
                    "pathHierarchy": "M-2",
                    "level": 2,
                    "certifiedNormalAccommodation": 3,
                    "workingCapacity": 3,
                    "maxCapacity": 6,
                    "locationType": "LANDING",
                    "subLocations": [
                      {
                        "cellMark": "M-1",
                        "pathHierarchy": "M-2-001",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      },
                      {
                        "cellMark": "M-2",
                        "pathHierarchy": "M-2-002",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      },
                      {
                        "cellMark": "M-3",
                        "pathHierarchy": "M-2-003",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL"
                      }
                    ]
                  }
                ]
              }
              ]
              }
          """,
            JsonCompareMode.LENIENT,
          )
        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
  }

  @DisplayName("PUT /certification/location/approve")
  @Nested
  inner class ApprovalRequestTest {

    private val url = "/certification/location/approve"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put().uri(url).bodyValue(
          jsonString(
            ApproveCertificationRequestDto(
              approvalRequestReference = UUID.randomUUID(),
              comments = "TEST",
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can approve a set of draft locations`() {
        webTestClient.get().uri("/locations/${mWing.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${mWing.getKey()}",
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "pendingChanges": {
                "maxCapacity": 12
              },
              "certification": {
                "certifiedNormalAccommodation": 0,
                "certified": false
              },
              "status": "DRAFT"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        val approvalId = webTestClient.put().uri("/certification/location/request-approval")
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
          .expectBody(CertificationApprovalRequestDto::class.java)
          .returnResult().responseBody!!.id

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalId,
                comments = "All locations OK",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "${mWing.getKey()}",
              "status": "APPROVED"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(9)

        webTestClient.get().uri("/locations/${mWing.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${mWing.getKey()}",
              "capacity": {
                "maxCapacity": 12,
                "workingCapacity": 0
              },
              "certification": {
                "certifiedNormalAccommodation": 6,
                "certified": true
              },
              "status": "INACTIVE",
              "childLocations": [
                {
                  "key": "LEI-M-1",
                  "childLocations": [
                    {
                      "key": "LEI-M-1-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-1-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-1-003",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    }
                  ]
                },
                {
                  "key": "LEI-M-2",
                  "childLocations": [
                    {
                      "key": "LEI-M-2-001",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-2-002",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-2-003",
                      "capacity": {
                        "maxCapacity": 2,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    }
                  ]
                }
              ]
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can approve a location that has pending CNA changes`() {
        webTestClient.get().uri("/locations/${cell1N.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${cell1N.getKey()}",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 2
              },
              "pendingChanges": {
                "maxCapacity": 3,
                "certifiedNormalAccommodation": 3
              },
              "certification": {
                "certifiedNormalAccommodation": 2,
                "certified": true
              },
              "status": "LOCKED_ACTIVE"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        val approvalRequestId = certificationApprovalRequestRepository.findByPrisonIdAndStatusOrderByRequestedDateDesc(cell1N.prisonId, ApprovalRequestStatus.PENDING).first()
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalRequestId.id!!,
                comments = "All OK",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "${cell1N.getKey()}",
              "status": "APPROVED",
              "comments": "All OK"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to cell1N.getKey(),
            "location.inside.prison.amended" to landingN1.getKey(),
            "location.inside.prison.amended" to landingN1.getParent()?.getKey(),
          )
        }

        assertThat((repository.findOneByKey(cell1N.getKey()) as Cell).getCertifiedNormalAccommodation()).isEqualTo(3)

        webTestClient.get().uri("/locations/${cell1N.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${cell1N.getKey()}",
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 2
              },
              "certification": {
                "certifiedNormalAccommodation": 3,
                "certified": true
              },
              "status": "ACTIVE"
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can approve request for a single cell`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalRequestId,
                comments = "All OK",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "LEI-A-1-001",
              "status": "APPROVED"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "LEI-A-1-001",
            "location.inside.prison.amended" to "LEI-A-1",
            "location.inside.prison.amended" to "LEI-A",
          )
        }

        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(3)

        webTestClient.get().uri("/locations/key/LEI-A-1-001")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "LEI-A-1-001",
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 1
              },
              "certification": {
                "certifiedNormalAccommodation": 1,
                "certified": true
              },
              "status": "ACTIVE"
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `after approval capacity is again back into pending`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalRequestId,
                comments = "All OK",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        Assertions.assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(3)

        val aCell = repository.findOneByKey("LEI-A-1-001") as Cell
        prisonerSearchMockServer.stubSearchByLocations("LEI", listOf(aCell.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${aCell.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              Capacity(
                workingCapacity = aCell.getWorkingCapacity() ?: 0,
                maxCapacity = 10,
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
        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(3)

        webTestClient.get().uri("/locations/key/LEI-A-1-001")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "LEI-A-1-001",
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 1
              },
              "pendingChanges": {
                "maxCapacity": 10
              },
              "status": "ACTIVE"
              }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /certification/location/reject")
  @Nested
  inner class RejectRequestTest {

    private val url = "/certification/location/reject"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put().uri(url).bodyValue(
          jsonString(
            RejectCertificationRequestDto(
              approvalRequestReference = UUID.randomUUID(),
              comments = "TEST",
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can reject request for a single cell`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              RejectCertificationRequestDto(
                approvalRequestReference = approvalRequestId,
                comments = "Bad",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "LEI-A-1-001",
              "status": "REJECTED"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "LEI-A-1-001",
            "location.inside.prison.amended" to "LEI-A-1",
            "location.inside.prison.amended" to "LEI-A",
          )
        }

        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(2)
      }
    }
  }

  @DisplayName("PUT /certification/location/withdraw")
  @Nested
  inner class WithdrawRequestTest {

    private val url = "/certification/location/withdraw"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put().uri(url).bodyValue(
          jsonString(
            WithdrawCertificationRequestDto(
              approvalRequestReference = UUID.randomUUID(),
              comments = "TEST",
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can withdraw request for a single cell`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              WithdrawCertificationRequestDto(
                approvalRequestReference = approvalRequestId,
                comments = "Not needed",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "LEI-A-1-001",
              "status": "WITHDRAWN"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "LEI-A-1-001",
            "location.inside.prison.amended" to "LEI-A-1",
            "location.inside.prison.amended" to "LEI-A",
          )
        }

        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(2)
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
