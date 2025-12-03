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
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInformation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInitialisationRequest
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SignedOpCapApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Certification Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class CertificationResourceTest(@param:Autowired private val locationService: LocationService) : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID
  private lateinit var mWing: ResidentialLocation
  private lateinit var aCell: Cell

  @Autowired
  lateinit var cellCertificateRepository: CellCertificateRepository

  @BeforeEach
  override fun setUp() {
    cellCertificateRepository.deleteAll()
    super.setUp()

    locationService.createCells(
      createCellsRequest = CellInitialisationRequest(
        prisonId = wingZ.prisonId,
        parentLocation = repository.findOneByKey("${wingZ.getKey()}-1")!!.id!!,
        cells = setOf(
          CellInformation(
            code = "NEW",
            cellMark = "NEW",
            certifiedNormalAccommodation = 2,
            maxCapacity = 3,
            workingCapacity = 2,
          ),
        ),
      ),
    )
    aCell = repository.findOneByKey("${wingZ.getKey()}-1-NEW") as Cell

    approvalRequestId = webTestClient.put().uri("/certification/location/request-approval")
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
      .expectBody<CertificationApprovalRequestDto>()
      .returnResult().responseBody!!.id

    prisonRegisterMockServer.stubLookupPrison("LEI")

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

  @DisplayName("PUT /certification/prison/signed-op-cap-change")
  @Nested
  inner class RequestSignedOpCapApprovalTest {

    private val url = "/certification/prison/signed-op-cap-change"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put().uri(url).bodyValue(
          jsonString(
            SignedOpCapApprovalRequest(
              prisonId = "LEI",
              signedOperationalCapacity = 300,
              reasonForChange = "Prison cells offline",
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class Validation {

      @Test
      fun `cannot request approval for a prison which has already been sent for approval`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              SignedOpCapApprovalRequest(
                prisonId = "LEI",
                signedOperationalCapacity = 11,
                reasonForChange = "Fire",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        assertThat(
          webTestClient.put().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                SignedOpCapApprovalRequest(
                  prisonId = "LEI",
                  signedOperationalCapacity = 10,
                  reasonForChange = "Flood",
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ApprovalRequestAlreadyExists.errorCode)
      }

      @Test
      fun `cannot request approval for a prison where max capacity is more than signed op cap`() {
        assertThat(
          webTestClient.put().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                SignedOpCapApprovalRequest(
                  prisonId = "LEI",
                  signedOperationalCapacity = 26,
                  reasonForChange = "We built more cells",
                ),
              ),
            )
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.SignedOpCapCannotBeMoreThanMaxCap.errorCode)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can request approval for a signed op cap change`() {
        locationService.createCells(
          createCellsRequest = CellInitialisationRequest(
            prisonId = mWing.prisonId,
            parentLocation = repository.findOneByKey("${mWing.getKey()}-1")!!.id!!,
            cells = setOf(
              CellInformation(
                code = "NEW",
                cellMark = "NEW",
                certifiedNormalAccommodation = 1,
                maxCapacity = 1,
                workingCapacity = 1,
              ),
            ),
          ),
        )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              SignedOpCapApprovalRequest(
                prisonId = "LEI",
                signedOperationalCapacity = 25,
                reasonForChange = "New Wing",
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "approvalType":"SIGNED_OP_CAP",
              "prisonId":"LEI",
              "status": "PENDING",
              "currentSignedOperationCapacity": 12,
              "signedOperationCapacityChange": 13,
              "reasonForSignedOpChange": "New Wing"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
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
      fun `cannot request approval for a location which is not the highest level with a pending change`() {
        val aLandingInDraft = mWing.findSubLocations().find { it.getKey() == "LEI-M-1" } ?: throw RuntimeException("Landing not found")

        Assertions.assertThat(
          webTestClient.put().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                LocationApprovalRequest(
                  locationId = aLandingInDraft.id!!,
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ApprovalRequestAtWrongLevel.errorCode)
      }

      @Test
      fun `cannot attempt to create a new location when pending approval above`() {
        webTestClient.put().uri("/certification/location/request-approval")
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

        val aLandingInDraft = mWing.findSubLocations().find { it.getKey() == "LEI-M-1" } ?: throw RuntimeException("Landing not found")

        Assertions.assertThat(
          webTestClient.post().uri("/locations/create-cells")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(createCellInitialisationRequest(startingCellNumber = 88, parentLocation = aLandingInDraft.id).copy(newLevelAboveCells = null))
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.CreationForbiddenWhenApprovalPending.errorCode)
      }

      @Test
      fun `cannot request approval for a location which has already been sent for approval`() {
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

        assertThat(
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
            .expectStatus().isEqualTo(409)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ApprovalRequestAlreadyExists.errorCode)
      }
    }

    @Nested
    inner class HappyPath {
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
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "usedFor": [
                  "STANDARD_ACCOMMODATION"
                ],
                "subLocations": [
                  {
                    "pathHierarchy": "M-1",
                    "level": 2,
                    "certifiedNormalAccommodation": 3,
                    "workingCapacity": 3,
                    "maxCapacity": 6,
                    "locationType": "LANDING",
                    "accommodationTypes": [
                      "NORMAL_ACCOMMODATION"
                    ],
                    "usedFor": [
                      "STANDARD_ACCOMMODATION"
                    ],
                    "subLocations": [
                      {
                        "cellMark": "M-1",
                        "pathHierarchy": "M-1-001",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
                      },
                      {
                        "cellMark": "M-2",
                        "pathHierarchy": "M-1-002",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
                      },
                      {
                        "cellMark": "M-3",
                        "pathHierarchy": "M-1-003",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
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
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
                      },
                      {
                        "cellMark": "M-2",
                        "pathHierarchy": "M-2-002",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
                      },
                      {
                        "cellMark": "M-3",
                        "pathHierarchy": "M-2-003",
                        "level": 3,
                        "certifiedNormalAccommodation": 1,
                        "workingCapacity": 1,
                        "maxCapacity": 2,
                        "inCellSanitation": true,
                        "locationType": "CELL",
                        "accommodationTypes": [
                          "NORMAL_ACCOMMODATION"
                        ],
                        "usedFor": [
                          "STANDARD_ACCOMMODATION"
                        ]
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
            ),
          ),
        ),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can approve a sign op cap change`() {
        val approvalId = webTestClient.put().uri("/certification/prison/signed-op-cap-change")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              SignedOpCapApprovalRequest(
                prisonId = "LEI",
                signedOperationalCapacity = 10,
                reasonForChange = "Broken door",
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
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "prisonId": "LEI",
              "status": "APPROVED"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/signed-op-cap/LEI")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "signedOperationCapacity": 10,
                "prisonId": "LEI"
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

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

        webTestClient.get().uri("/locations/residential-summary/${mWing.prisonId}?parentPathHierarchy=${mWing.getLocationCode()}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "parentLocation": {
                  "pathHierarchy": "M",
                  "topLevelApprovalLocationId": "${mWing.id}",
                  "pendingApprovalRequestId": "$approvalId"
                },
                "subLocations": [
                  {
                  "pathHierarchy": "M-1",
                  "topLevelApprovalLocationId": "${mWing.id}",
                  "pendingApprovalRequestId": "$approvalId"
                  },
                  {
                  "pathHierarchy": "M-2",
                  "topLevelApprovalLocationId": "${mWing.id}",
                  "pendingApprovalRequestId": "$approvalId"
                  }
                ]
                }
               """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalId,
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
        getDomainEvents(9).let {
          assertThat(it).hasSize(9)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "LEI-M-1-001",
            "location.inside.prison.created" to "LEI-M-1-002",
            "location.inside.prison.created" to "LEI-M-1-003",
            "location.inside.prison.created" to "LEI-M-2-001",
            "location.inside.prison.created" to "LEI-M-2-002",
            "location.inside.prison.created" to "LEI-M-2-003",
            "location.inside.prison.created" to "LEI-M-1",
            "location.inside.prison.created" to "LEI-M-2",
            "location.inside.prison.created" to "LEI-M",
          )
        }
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
        webTestClient.get().uri("/locations/${aCell.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${aCell.getKey()}",
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0,
                "certifiedNormalAccommodation": 0
              },
              "pendingChanges": {
                "maxCapacity": 3,
                "workingCapacity": 2,
                "certifiedNormalAccommodation": 2
              },
              "certifiedCell": false,
              "status": "LOCKED_DRAFT"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        val approvalRequestId = certificationApprovalRequestRepository.findByPrisonIdAndStatusOrderByRequestedDateDesc(aCell.prisonId, ApprovalRequestStatus.PENDING).first()
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              ApproveCertificationRequestDto(
                approvalRequestReference = approvalRequestId.id!!,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "${aCell.getKey()}",
              "status": "APPROVED"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to aCell.getKey(),
            "location.inside.prison.amended" to aCell.getParent()?.getKey(),
            "location.inside.prison.amended" to aCell.getParent()?.getParent()?.getKey(),
          )
        }

        assertThat((repository.findOneByKey(aCell.getKey()) as Cell).getCertifiedNormalAccommodation()).isEqualTo(2)

        webTestClient.get().uri("/locations/${aCell.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "${aCell.getKey()}",
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 0,
                "certifiedNormalAccommodation": 2
              },
              "oldWorkingCapacity": 2,
              "certifiedCell": true,
              "status": "INACTIVE"
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
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "locationKey": "MDI-Z-1-NEW",
              "status": "APPROVED"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "MDI-Z-1-NEW",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        assertThat((repository.findOneByKey("MDI-Z-1-NEW") as Cell).getMaxCapacity()).isEqualTo(3)

        webTestClient.get().uri("/locations/key/MDI-Z-1-NEW")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
              "key": "MDI-Z-1-NEW",
              "capacity": {
                "maxCapacity": 3,
                "workingCapacity": 0,
                "certifiedNormalAccommodation": 2
              },
              "oldWorkingCapacity": 2,
              "certifiedCell": true,
              "status": "INACTIVE"
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
              "locationKey": "${aCell.getKey()}",
              "status": "REJECTED"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero
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
              "locationKey": "MDI-Z-1-NEW",
              "status": "WITHDRAWN"
              }
          """,
            JsonCompareMode.LENIENT,
          )
        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
  }
}
