package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellDraftUpdateRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInformation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellInitialisationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateWingAndStructureRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LevelAboveCells
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ResidentialStructuralType
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialSummary
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.*

@WithMockAuthUser(username = EXPECTED_USERNAME)
class DraftLocationResourceTest : CommonDataTestBase() {

  lateinit var draftWing: ResidentialLocation
  lateinit var landing1G: ResidentialLocation

  @BeforeEach
  override fun setUp() {
    super.setUp()
    // Create a draft wing in Leeds prison
    draftWing = repository.saveAndFlush(
      CreateEntireWingRequest(
        prisonId = "LEI",
        wingCode = "G",
        numberOfCellsPerSection = 3,
        numberOfLandings = 2,
        numberOfSpurs = 0,
        wingDescription = "Wing G",
      ).toEntity(
        createInDraft = true,
        createdBy = "TEST_USER",
        clock = clock,
        linkedTransaction = linkedTransactionRepository.saveAndFlush(
          LinkedTransaction(
            prisonId = "LEI",
            transactionType = TransactionType.LOCATION_CREATE,
            transactionDetail = "Initial Data Load for Leeds",
            transactionInvokedBy = EXPECTED_USERNAME,
            txStartTime = LocalDateTime.now(clock).minusDays(1),
          ),
        ),
      ),
    )
    landing1G = resiRepository.findOneByKey("LEI-G-1") ?: throw AssertionError("Not found LEI-G-1")
  }

  @DisplayName("POST /locations/create-cells")
  @Nested
  inner class CreateCellsTest {
    private val url = "/locations/create-cells"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.post(),
        url,
        createCellInitialisationRequest(),
        "ROLE_MAINTAIN_LOCATIONS",
        "write",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              createCellInitialisationRequest(
                aboveLevelCode = landingZ1.getLocationCode(),
                parentLocation = wingZ.id,
              ),
            ),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `location created that does not fit wing structure`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              createCellInitialisationRequest(
                aboveLevelCode = "TEST",
                parentLocation = leedsWing.id,
                locationType = ResidentialStructuralType.SPUR,
              ),
            ),
          )
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `request without a parent location or a new location is rejected`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createCellInitialisationRequest().copy(newLevelAboveCells = null)))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `request with clashing cell locations is rejected`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(createCellInitialisationRequest(parentLocation = landingZ1.id).copy(newLevelAboveCells = null))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot create a cell with max cap greater than working cap`() {
        val request = createCellInitialisationRequest(parentLocation = wingZ.id, maxCap = 1, workingCap = 2)
        assertThat(
          webTestClient.post().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.WorkingCapacityExceedsMaxCapacity.errorCode)
      }

      @Test
      fun `request with normal cells without specialist cells do not allow zero CNA or Working Capacity`() {
        assertThat(
          webTestClient.post().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(createCellInitialisationRequest(workingCap = 0, cna = 0, specialistCellTypes = emptySet()))
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed.errorCode)
      }

      @Test
      fun `request with normal cell combination of specialist cells that do and do not not allow zero CNA or Working Capacity fails`() {
        assertThat(
          webTestClient.post().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              createCellInitialisationRequest(
                workingCap = 0,
                cna = 0,
                specialistCellTypes = setOf(SpecialistCellType.ESCAPE_LIST, SpecialistCellType.CSU),
              ),
            )
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed.errorCode)
      }

      @Test
      fun `request with specialist cells which are not capacity affecting do not allow zero CNA or Working Capacity`() {
        assertThat(
          webTestClient.post().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              createCellInitialisationRequest(
                workingCap = 0,
                cna = 0,
                specialistCellTypes = setOf(SpecialistCellType.ESCAPE_LIST),
              ),
            )
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed.errorCode)
      }

      @Test
      fun `request with specialist cells with zero CNA or Working Capacity are allowed`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            createCellInitialisationRequest(
              workingCap = 0,
              cna = 0,
              specialistCellTypes = setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST),
            ),
          )
          .exchange()
          .expectStatus().isCreated
      }
    }

    @Test
    fun `request with specialist multiple cells with zero CNA or Working Capacity are allowed`() {
      webTestClient.post().uri(url)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          createCellInitialisationRequest(
            workingCap = 0,
            cna = 0,
            specialistCellTypes = setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST, SpecialistCellType.CSU),
          ),
        )
        .exchange()
        .expectStatus().isCreated
    }

    @Test
    fun `request with Care and separation accommodation type with zero CNA or Working Capacity are allowed`() {
      webTestClient.post().uri(url)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          createCellInitialisationRequest(
            workingCap = 0,
            cna = 0,
            accommodationType = AccommodationType.CARE_AND_SEPARATION,
          ),
        )
        .exchange()
        .expectStatus().isCreated
    }

    @Test
    fun `request with healthcare accommodation type with zero CNA or Working Capacity are allowed`() {
      webTestClient.post().uri(url)
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          createCellInitialisationRequest(
            workingCap = 0,
            cna = 0,
            accommodationType = AccommodationType.HEALTHCARE_INPATIENTS,
          ),
        )
        .exchange()
        .expectStatus().isCreated
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create a landing and a cell`() {
        val request = createCellInitialisationRequest(parentLocation = wingZ.id)
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${request.prisonId}",
              "code": "${request.newLevelAboveCells?.levelCode}",
              "pathHierarchy": "${wingZ.getLocationCode()}-${request.newLevelAboveCells?.levelCode}",
              "locationType": "LANDING",
              "status": "DRAFT",
              "topLevelApprovalLocationId": "${repository.findOneByKey("${request.prisonId}-${wingZ.getLocationCode()}-${request.newLevelAboveCells?.levelCode}")?.id}",
              "key": "${request.prisonId}-${wingZ.getLocationCode()}-${request.newLevelAboveCells?.levelCode}",
              "localName": "LANDING J",
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "pendingChanges": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              },
              "childLocations": [
                {
                  "key": "MDI-Z-J-001",
                  "cellMark": "J-001",
                  "locationType": "CELL",
                  "topLevelApprovalLocationId": "${repository.findOneByKey("${request.prisonId}-${wingZ.getLocationCode()}-${request.newLevelAboveCells?.levelCode}")?.id}",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "pendingChanges": {
                    "maxCapacity": 1,
                    "workingCapacity": 1
                  },
                  "certification": {
                    "certified": false,
                    "certifiedNormalAccommodation": 0
                  },
                  "accommodationTypes": [
                    "NORMAL_ACCOMMODATION"
                  ],
                  "specialistCellTypes": [
                    "ACCESSIBLE_CELL"
                  ],
                  "usedFor": [
                    "STANDARD_ACCOMMODATION"
                  ],
                  "status": "DRAFT",
                  "inCellSanitation": true,
                  "level": 3,
                  "leafLevel": true
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can create a cell below a top level location`() {
        val request = createCellInitialisationRequest(locationType = ResidentialStructuralType.WING)
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${request.prisonId}",
              "code": "${request.newLevelAboveCells?.levelCode}",
              "pathHierarchy": "${request.newLevelAboveCells?.levelCode}",
              "locationType": "WING",
              "status": "DRAFT",
              "topLevelApprovalLocationId": "${repository.findOneByKey("MDI-J")?.id}",
              "key": "${request.prisonId}-${request.newLevelAboveCells?.levelCode}",
              "localName": "WING J",
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "pendingChanges": {
                "maxCapacity": 1,
                "workingCapacity": 1
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              },
              "childLocations": [
                {
                  "key": "MDI-J-001",
                  "cellMark": "J-001",
                  "locationType": "CELL",
                  "topLevelApprovalLocationId": "${repository.findOneByKey("MDI-J")?.id}",
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "pendingChanges": {
                    "maxCapacity": 1,
                    "workingCapacity": 1
                  },
                  "certification": {
                    "certified": false,
                    "certifiedNormalAccommodation": 0
                  },
                  "accommodationTypes": [
                    "NORMAL_ACCOMMODATION"
                  ],
                  "specialistCellTypes": [
                    "ACCESSIBLE_CELL"
                  ],
                  "usedFor": [
                    "STANDARD_ACCOMMODATION"
                  ],
                  "status": "DRAFT",
                  "inCellSanitation": true,
                  "level": 2,
                  "leafLevel": true
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can create a cell below an existing level location`() {
        val request = createCellInitialisationRequest(startingCellNumber = 10, parentLocation = landingZ1.id).copy(newLevelAboveCells = null)
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${request.prisonId}",
              "code": "${landingZ1.getLocationCode()}",
              "pathHierarchy": "${landingZ1.getPathHierarchy()}",
              "locationType": "${landingZ1.locationType}",
              "status": "ACTIVE",
              "key": "${landingZ1.getKey()}",
              "localName": "Landing 1",
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION",
                "CARE_AND_SEPARATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ],
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "pendingChanges": {
                "maxCapacity": 5,
                "workingCapacity": 5
              },
              "certification": {
                "certified": true,
                "certifiedNormalAccommodation": 4
              },
              "childLocations": [
                {
                  "key": "MDI-Z-1-001",
                  "status": "ACTIVE"
                },
                {
                  "key": "MDI-Z-1-002",
                  "status": "ACTIVE"
                },
                {
                  "key": "MDI-Z-1-01S",
                  "status": "ACTIVE"
                },  
                {
                  "key": "MDI-Z-1-010",
                  "status": "DRAFT",
                  "topLevelApprovalLocationId": "${repository.findOneByKey("MDI-Z-1-010")?.id}",
                  "inCellSanitation": true,
                  "capacity": {
                    "maxCapacity": 0,
                    "workingCapacity": 0
                  },
                  "pendingChanges": {
                    "maxCapacity": 1,
                    "workingCapacity": 1
                  }
                }  
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can create just a landing with no cells`() {
        val request = createCellInitialisationRequest(numberOfCells = 0)
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${request.prisonId}",
              "code": "${request.newLevelAboveCells?.levelCode}",
              "pathHierarchy": "${request.newLevelAboveCells?.levelCode}",
              "locationType": "LANDING",
              "status": "DRAFT",
              "key": "${request.prisonId}-${request.newLevelAboveCells?.levelCode}",
              "localName": "LANDING J",
              "accommodationTypes": [],
              "specialistCellTypes": [],
              "usedFor": [ ],
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              },
              "certification": {
                "certified": false,
                "certifiedNormalAccommodation": 0
              },
              "childLocations": []
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
  }

  @DisplayName("PUT /locations/edit-cells")
  @Nested
  inner class EditCellsTest {
    private val url = "/locations/edit-cells"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.put(),
        url,
        createCellDraftUpdateRequest(parentLocation = landing1G.id!!),
        "ROLE_MAINTAIN_LOCATIONS",
        "write",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `request without a parent location is rejected`() {
        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": "MDI"}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update a cell with max cap greater than working cap`() {
        val request = createCellDraftUpdateRequest(
          parentLocation = landing1G.id!!,
          cells = landing1G.cellLocations().map { it.toCellInformation(specialistCellTypes = setOf(SpecialistCellType.ISOLATION_DISEASES, SpecialistCellType.ACCESSIBLE_CELL)).copy(maxCapacity = 1, workingCapacity = 2) },
          usedForTypes = setOf(UsedForType.THERAPEUTIC_COMMUNITY),
          accommodationType = AccommodationType.HEALTHCARE_INPATIENTS,
        )
        assertThat(
          webTestClient.put().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.WorkingCapacityExceedsMaxCapacity.errorCode)
      }

      @Test
      fun `cannot create a cell with max cap greater than working cap`() {
        val request = createCellDraftUpdateRequest(
          parentLocation = landing1G.id!!,
          cells = landing1G.cellLocations().map { it.toCellInformation() }.plus(
            CellInformation(
              code = "010",
              cellMark = "NEW10",
              maxCapacity = 1,
              workingCapacity = 2,
              certifiedNormalAccommodation = 1,
              specialistCellTypes = setOf(SpecialistCellType.ESCAPE_LIST),
              inCellSanitation = false,
            ),
          ),
        )
        assertThat(
          webTestClient.put().uri(url)
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.WorkingCapacityExceedsMaxCapacity.errorCode)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can update cells`() {
        val request = createCellDraftUpdateRequest(
          parentLocation = landing1G.id!!,
          cells = landing1G.cellLocations().map { it.toCellInformation(specialistCellTypes = setOf(SpecialistCellType.ISOLATION_DISEASES, SpecialistCellType.ACCESSIBLE_CELL)) },
          usedForTypes = setOf(UsedForType.THERAPEUTIC_COMMUNITY),
          accommodationType = AccommodationType.HEALTHCARE_INPATIENTS,
        )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${landing1G.prisonId}",
              "code": "${landing1G.getLocationCode()}",
              "pathHierarchy": "${landing1G.getPathHierarchy()}",
              "locationType": "LANDING",
              "status": "DRAFT",
              "localName": "Landing 1 on Wing G",
              "pendingChanges": {
                "maxCapacity": 3,
                "workingCapacity": 3,
                "certifiedNormalAccommodation": 3
              },
              "accommodationTypes": [
                "HEALTHCARE_INPATIENTS"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL",
                "ISOLATION_DISEASES"
              ],
              "usedFor": [
                "THERAPEUTIC_COMMUNITY"
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can remove cells`() {
        val request = createCellDraftUpdateRequest(
          parentLocation = landing1G.id!!,
          cells = landing1G.cellLocations().minus(landing1G.cellLocations().first()).map { it.toCellInformation() },
        )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${landing1G.prisonId}",
              "code": "${landing1G.getLocationCode()}",
              "pathHierarchy": "${landing1G.getPathHierarchy()}",
              "locationType": "LANDING",
              "status": "DRAFT",
              "localName": "Landing 1 on Wing G",
              "pendingChanges": {
                "maxCapacity": 2,
                "workingCapacity": 2,
                "certifiedNormalAccommodation": 2
              },
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ACCESSIBLE_CELL"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can add cells`() {
        val request = createCellDraftUpdateRequest(
          parentLocation = landing1G.id!!,
          cells = landing1G.cellLocations().map { it.toCellInformation() }.plus(
            CellInformation(
              code = "010",
              cellMark = "NEW10",
              maxCapacity = 2,
              workingCapacity = 1,
              certifiedNormalAccommodation = 1,
              specialistCellTypes = setOf(SpecialistCellType.ESCAPE_LIST),
              inCellSanitation = false,
            ),
          ),
        )

        webTestClient.put().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(request)
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "${landing1G.prisonId}",
              "code": "${landing1G.getLocationCode()}",
              "pathHierarchy": "${landing1G.getPathHierarchy()}",
              "locationType": "LANDING",
              "status": "DRAFT",
              "localName": "Landing 1 on Wing G",
              "pendingChanges": {
                "maxCapacity": 5,
                "workingCapacity": 4,
                "certifiedNormalAccommodation": 4
              },
              "accommodationTypes": [
                "NORMAL_ACCOMMODATION"
              ],
              "specialistCellTypes": [
                "ESCAPE_LIST",
                "ACCESSIBLE_CELL"
              ],
              "usedFor": [
                "STANDARD_ACCOMMODATION"
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
  }

  @DisplayName("POST /locations/create-wing")
  @Nested
  inner class CreateWingTest {
    var createWingAndStructure = CreateWingAndStructureRequest(
      prisonId = "MDI",
      wingCode = "Y",
      wingDescription = "Y Wing",
      wingStructure = listOf(ResidentialStructuralType.WING, ResidentialStructuralType.LANDING, ResidentialStructuralType.CELL),
    )

    private val url = "/locations/create-wing"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.post(),
        url,
        createWingAndStructure,
        "ROLE_MAINTAIN_LOCATIONS",
        "write",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createWingAndStructure.copy(wingCode = "Z")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create a wing with a 3 tier structure`() {
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(createWingAndStructure)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "Y",
              "pathHierarchy": "Y",
              "locationType": "WING",
              "status":"DRAFT",
              "topLevelApprovalLocationId": "${repository.findOneByKey("MDI-Y")?.id}",
              "key": "MDI-Y",
              "localName": "Y Wing",
              "wingStructure": [
                "WING",
                "LANDING",
                "CELL"
              ],
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()

        assertThat(
          webTestClient.get().uri("/locations/residential-summary/${createWingAndStructure.prisonId}?parentPathHierarchy=${createWingAndStructure.wingCode}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
            .exchange()
            .expectStatus().isOk
            .expectBody(ResidentialSummary::class.java)
            .returnResult().responseBody!!.subLocationName,
        ).isEqualTo("Landings")
      }

      @Test
      fun `can create a wing with a 4 tier structure`() {
        val fourTierStructure = createWingAndStructure.copy(
          wingStructure = listOf(
            ResidentialStructuralType.WING,
            ResidentialStructuralType.SPUR,
            ResidentialStructuralType.LANDING,
            ResidentialStructuralType.CELL,
          ),
        )
        webTestClient.post().uri(url)
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(fourTierStructure)
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "Y",
              "pathHierarchy": "Y",
              "locationType": "WING",
              "status":"DRAFT",
              "key": "MDI-Y",
              "localName": "Y Wing",
              "topLevelApprovalLocationId": "${repository.findOneByKey("MDI-Y")?.id}",
              "wingStructure": [
                "WING",
                "SPUR",
                "LANDING",
                "CELL"
              ],
              "capacity": {
                "maxCapacity": 0,
                "workingCapacity": 0
              }
            }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()

        assertThat(
          webTestClient.get().uri("/locations/residential-summary/${fourTierStructure.prisonId}?parentPathHierarchy=${fourTierStructure.wingCode}")
            .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
            .exchange()
            .expectStatus().isOk
            .expectBody(ResidentialSummary::class.java)
            .returnResult().responseBody!!.subLocationName,
        ).isEqualTo("Spurs")
      }
    }
  }

  @DisplayName("DELETE /locations/{id}")
  @Nested
  inner class DeleteDraftLocationTest {

    @Nested
    inner class Validation {
      @Test
      fun `location not found`() {
        webTestClient.delete().uri("/locations/${UUID.randomUUID()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `location is not a draft`() {
        webTestClient.delete().uri("/locations/${wingZ.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can delete a cell draft location`() {
        val cell1 = draftWing.findAllLeafLocations().first()
        webTestClient.delete().uri("/locations/${cell1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isNoContent

        assertThat(repository.findById(cell1.id!!)).isEmpty

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can delete a landing draft location`() {
        val landing1 = draftWing.findSubLocations().find { it.getKey() == "LEI-G-1" }
        webTestClient.delete().uri("/locations/${landing1!!.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isNoContent

        assertThat(repository.findById(landing1.id!!)).isEmpty

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }

      @Test
      fun `can delete a wing draft location`() {
        webTestClient.delete().uri("/locations/${draftWing.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isNoContent

        assertThat(repository.findById(draftWing.id!!)).isEmpty

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isZero()
      }
    }
  }
}

fun createCellInitialisationRequest(
  prisonId: String = "MDI",
  startingCellNumber: Int = 1,
  numberOfCells: Int = 1,
  aboveLevelCode: String = "J",
  parentLocation: UUID? = null,
  maxCap: Int = 1,
  workingCap: Int = 1,
  cna: Int = 1,
  specialistCellTypes: Set<SpecialistCellType> = setOf(SpecialistCellType.ACCESSIBLE_CELL),
  locationType: ResidentialStructuralType = ResidentialStructuralType.LANDING,
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
) = CellInitialisationRequest(
  prisonId = prisonId,
  parentLocation = parentLocation,
  newLevelAboveCells = LevelAboveCells(
    levelCode = aboveLevelCode,
    locationType = locationType,
    levelLocalName = "$locationType $aboveLevelCode",
  ),
  accommodationType = accommodationType,
  cellsUsedFor = setOf(UsedForType.STANDARD_ACCOMMODATION),
  cells = (maxCap..numberOfCells).map { index ->
    CellInformation(
      code = "%03d".format(index - maxCap + startingCellNumber),
      cellMark = "$aboveLevelCode-%03d".format(index - maxCap + startingCellNumber),
      maxCapacity = maxCap,
      workingCapacity = workingCap,
      certifiedNormalAccommodation = cna,
      specialistCellTypes = specialistCellTypes,
      inCellSanitation = true,
    )
  }.toSet(),
)

fun createCellDraftUpdateRequest(
  prisonId: String = "LEI",
  parentLocation: UUID,
  cells: List<CellInformation> = listOf(),
  accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,
  usedForTypes: Set<UsedForType> = setOf(UsedForType.STANDARD_ACCOMMODATION),
) = CellDraftUpdateRequest(
  prisonId = prisonId,
  parentLocation = parentLocation,
  cellsUsedFor = usedForTypes,
  accommodationType = accommodationType,
  cells = cells.toSet(),
)

fun Cell.toCellInformation(specialistCellTypes: Set<SpecialistCellType> = setOf(SpecialistCellType.ACCESSIBLE_CELL)) = CellInformation(
  id = id!!,
  code = getLocationCode(),
  cellMark = cellMark,
  maxCapacity = getMaxCapacity()!!,
  workingCapacity = getWorkingCapacity()!!,
  certifiedNormalAccommodation = getCertifiedNormalAccommodation()!!,
  specialistCellTypes = specialistCellTypes,
  inCellSanitation = inCellSanitation ?: false,
)
