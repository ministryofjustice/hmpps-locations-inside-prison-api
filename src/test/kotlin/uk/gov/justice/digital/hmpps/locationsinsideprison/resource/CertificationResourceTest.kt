package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.RejectCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.WithdrawCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Certification Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationResourceTest : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID
  private lateinit var mWing: ResidentialLocation

  @BeforeEach
  override fun setUp() {
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
              "status": "PENDING"
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
              "pendingCapacity": {
                 "maxCapacity": 6,
                 "workingCapacity": 6
              },
              "certification": {
                "capacityOfCertifiedCell": 6,
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
                "maxCapacity": 6,
                "workingCapacity": 0
              },
              "certification": {
                "capacityOfCertifiedCell": 6,
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
                        "maxCapacity": 1,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-1-002",
                      "capacity": {
                        "maxCapacity": 1,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-1-003",
                      "capacity": {
                        "maxCapacity": 1,
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
                        "maxCapacity": 1,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-2-002",
                      "capacity": {
                        "maxCapacity": 1,
                        "workingCapacity": 0
                      },
                      "oldWorkingCapacity": 1
                    },
                     {
                      "key": "LEI-M-2-003",
                      "capacity": {
                        "maxCapacity": 1,
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

        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(1)
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

        assertThat((repository.findOneByKey("LEI-A-1-001") as Cell).getMaxCapacity()).isEqualTo(1)
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
