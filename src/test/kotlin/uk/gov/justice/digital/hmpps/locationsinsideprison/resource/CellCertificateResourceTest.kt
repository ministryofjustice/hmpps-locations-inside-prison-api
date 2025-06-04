package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ApproveCertificationRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateEntireWingRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationApprovalRequest
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime
import java.util.UUID

@DisplayName("Cell Certificate Resource")
@WithMockAuthUser(username = EXPECTED_USERNAME)
class CellCertificateResourceTest : CommonDataTestBase() {

  private lateinit var approvalRequestId: UUID
  private lateinit var cellCertificateId: UUID
  private lateinit var mWing: ResidentialLocation

  @Autowired
  lateinit var cellCertificateRepository: CellCertificateRepository

  @BeforeEach
  override fun setUp() {
    super.setUp()
    cellCertificateRepository.deleteAll()

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

    // Request approval for the wing
    approvalRequestId = webTestClient.put().uri("/certification/location/request-approval")
      .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
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
      .expectBody(CertificationApprovalRequestDto::class.java)
      .returnResult().responseBody!!.id

    // Approve the request to generate a cell certificate
    webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          ApproveCertificationRequestDto(
            approvalRequestReference = approvalRequestId,
            comments = "All locations OK",
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk

    // Get the cell certificate ID
    val certificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("LEI")
    cellCertificateId = certificate!!.id!!
  }

  private fun getSingleCellApprovalRequestId(): UUID {
    val aCell = repository.findOneByKey("LEI-M-1-002") as Cell
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

  private fun createCertificateForSingleCell(): UUID {
    val approvalRequestId = getSingleCellApprovalRequestId()

    webTestClient.put().uri("/certification/location/approve")
      .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
      .header("Content-Type", "application/json")
      .bodyValue(
        jsonString(
          ApproveCertificationRequestDto(
            approvalRequestReference = approvalRequestId,
            comments = "Cell ok",
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk

    // Get the cell certificate ID
    val certificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("LEI")
    return certificate!!.id!!
  }

  @DisplayName("GET /cell-certificates/{id}")
  @Nested
  inner class GetCellCertificateTest {

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri("/cell-certificates/${UUID.randomUUID()}"),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `returns 404 when cell certificate not found`() {
        val nonExistentId = UUID.randomUUID()
        webTestClient.get().uri("/cell-certificates/$nonExistentId")
          .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("$.userMessage").exists()
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can get cell certificate by ID`() {
        webTestClient.get().uri("/cell-certificates/$cellCertificateId")
          .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.id").isEqualTo(cellCertificateId.toString())
          .jsonPath("$.prisonId").isEqualTo("LEI")
          .jsonPath("$.approvedBy").isEqualTo(EXPECTED_USERNAME)
          .jsonPath("$.current").isEqualTo(true)
      }
    }
  }

  @DisplayName("GET /cell-certificates/prison/{prisonId}")
  @Nested
  inner class GetCellCertificatesForPrisonTest {

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri("/cell-certificates/prison/LEI"),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can get all cell certificates for a prison`() {
        webTestClient.get().uri("/cell-certificates/prison/LEI")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$[0].id").isEqualTo(cellCertificateId.toString())
      }

      @Test
      fun `returns empty list when no cell certificates found for prison`() {
        webTestClient.get().uri("/cell-certificates/prison/MDI")
          .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json("[]")
      }
    }
  }

  @DisplayName("GET /cell-certificates/prison/{prisonId}/current")
  @Nested
  inner class GetCurrentCellCertificateForPrisonTest {

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri("/cell-certificates/prison/LEI/current"),
        "ROLE_LOCATION_CERTIFICATION",
      )
    }

    @Nested
    inner class Validation {
      @Test
      fun `returns 404 when no current cell certificate found for prison`() {
        // Use a prison ID that definitely doesn't exist in the test data
        val nonExistentPrisonId = "XYI"

        webTestClient.get().uri("/cell-certificates/prison/$nonExistentPrisonId/current")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("$.userMessage").exists()
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can get current cell certificate for a prison`() {
        webTestClient.get().uri("/cell-certificates/prison/LEI/current")
          .headers(setAuthorisation(user = EXPECTED_USERNAME, roles = listOf("ROLE_LOCATION_CERTIFICATION")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.id").isEqualTo(cellCertificateId.toString())
          .jsonPath("$.prisonId").isEqualTo("LEI")
          .jsonPath("$.approvedBy").isEqualTo(EXPECTED_USERNAME)
          .jsonPath("$.current").isEqualTo(true)
      }
    }
  }

  @DisplayName("Successful approval generates a cell certificate")
  @Nested
  inner class SuccessfulApprovalTest {
    @Test
    fun `successful approval of an approval request generates a cell certificate that captures all cells in the prison`() {
      // generate another approval request for a single cell - this should not affect the existing certificate
      getSingleCellApprovalRequestId()
      // Verify that the cell certificate was created with the correct data
      webTestClient.get().uri("/cell-certificates/$cellCertificateId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(cellCertificateId.toString())
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.approvedBy").isEqualTo(EXPECTED_USERNAME)
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(12)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(12)
        .jsonPath("$.totalCapacityOfCertifiedCell").isEqualTo(12)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[1].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[1].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[1].subLocations[1].subLocations.length()").isEqualTo(3)
    }

    @Test
    fun `successful approval of a single cell change generates a cell certificate that captures all cells in the prison`() {
      val latestCertificateId = createCertificateForSingleCell()
      // Verify that the cell certificate was created with the correct data
      webTestClient.get().uri("/cell-certificates/$latestCertificateId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(latestCertificateId.toString())
        .jsonPath("$.prisonId").isEqualTo("LEI")
        .jsonPath("$.approvedBy").isEqualTo(EXPECTED_USERNAME)
        .jsonPath("$.current").isEqualTo(true)
        .jsonPath("$.locations").isArray()
        .jsonPath("$.totalMaxCapacity").isEqualTo(14)
        .jsonPath("$.totalWorkingCapacity").isEqualTo(12)
        .jsonPath("$.totalCapacityOfCertifiedCell").isEqualTo(12)
        // Verify that there are locations in the response
        .jsonPath("$.locations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[0].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[0].subLocations[1].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[1].subLocations.length()").isEqualTo(2)
        .jsonPath("$.locations[1].subLocations[0].subLocations.length()").isEqualTo(3)
        .jsonPath("$.locations[1].subLocations[1].subLocations.length()").isEqualTo(3)
    }
  }
}
