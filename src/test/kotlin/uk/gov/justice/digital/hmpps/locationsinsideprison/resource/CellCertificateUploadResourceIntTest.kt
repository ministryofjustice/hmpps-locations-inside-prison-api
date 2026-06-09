package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadListenerService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime

class CellCertificateUploadResourceIntTest : CommonDataTestBase() {

  @Autowired
  lateinit var cellCertificateUploadRepository: CellCertificateUploadRepository

  @MockitoSpyBean
  lateinit var cellCertificateUploadListenerService: CellCertificateUploadListenerService

  @Autowired
  lateinit var transactionManager: PlatformTransactionManager

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  private val uploadQueue by lazy { hmppsQueueService.findByQueueId(UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY) as HmppsQueue }

  @BeforeEach
  fun cleanUploads() {
    uploadQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(uploadQueue.queueUrl).build())
    cellCertificateUploadRepository.deleteAll()
  }

  private fun requestBody() = jsonString(
    UpdateCapacityRequest(
      locations = mapOf(
        "MDI-Z-1-001" to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2, inCellSanitation = true, cellMark = "A001"),
        "MDI-Z-1-002" to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 2, certifiedNormalAccommodation = 1, cellMark = "A002"),
      ),
    ),
  )

  @DisplayName("POST /locations/bulk/update-cell-certificate/{prisonId}")
  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf()))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with right role wrong scope`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class HappyPath {
    @Test
    fun `stores the upload, returns 202 with PENDING status and queues processing`() {
      val response = webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isAccepted
        .expectBody()
        .jsonPath("$.prisonId").isEqualTo("MDI")
        .jsonPath("$.status").isEqualTo("PENDING") // synchronous response returns the just-stored state
        .jsonPath("$.totalRecords").isEqualTo(2)
        .jsonPath("$.id").exists()
        .returnResult()
      assertThat(response.responseBody).isNotNull

      // a START_PROCESSING message was sent and delivered to the listener
      await untilAsserted {
        verify(cellCertificateUploadListenerService).onEventReceived(any())
      }

      // the queued message drives processing through to completion (lazy collection read in a transaction)
      await untilAsserted {
        assertThat(cellCertificateUploadRepository.findAll().firstOrNull()?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
      }
      TransactionTemplate(transactionManager).execute {
        val upload = cellCertificateUploadRepository.findAll().first()
        assertThat(upload.prisonId).isEqualTo("MDI")
        assertThat(upload.totalRecords).isEqualTo(2)
        assertThat(upload.locations).hasSize(2)
        assertThat(upload.locations.map { it.locationKey }).containsExactlyInAnyOrder("MDI-Z-1-001", "MDI-Z-1-002")
        assertThat(upload.locations).noneMatch { it.status == CellCertificateUploadLocationStatus.PENDING }
      }
    }
  }

  @Nested
  inner class Failures {
    @Test
    fun `returns 404 when prison does not exist`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/XXX")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `returns 400 when prison requires approval and reason for change is missing`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/LEI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `returns 409 when an upload is already in progress for the prison`() {
      cellCertificateUploadRepository.saveAndFlush(
        CellCertificateUpload(
          prisonId = "MDI",
          status = CellCertificateUploadStatus.PENDING,
          requestedBy = EXPECTED_USERNAME,
          requestedDate = LocalDateTime.now(clock),
          totalRecords = 0,
        ),
      )

      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isEqualTo(409)
    }
  }

  @Nested
  inner class ListAndDrillDown {
    private fun saveUpload(prisonId: String, status: CellCertificateUploadStatus, daysAgo: Long, rows: List<CellCertificateUploadLocation> = emptyList()) = cellCertificateUploadRepository.saveAndFlush(
      CellCertificateUpload(
        prisonId = prisonId,
        status = status,
        requestedBy = EXPECTED_USERNAME,
        requestedDate = LocalDateTime.now(clock).minusDays(daysAgo),
        totalRecords = rows.size,
      ).apply { rows.forEach { addLocation(it) } },
    )

    @Test
    fun `lists uploads for a prison most recent first and filters by status`() {
      val finished = saveUpload("MDI", CellCertificateUploadStatus.FINISHED, daysAgo = 2)
      val processing = saveUpload("MDI", CellCertificateUploadStatus.PENDING, daysAgo = 1)
      saveUpload("LEI", CellCertificateUploadStatus.FINISHED, daysAgo = 1) // other prison, excluded

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].id").isEqualTo(processing.id.toString()) // most recent first
        .jsonPath("$[1].id").isEqualTo(finished.id.toString())

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/MDI?status=COMPLETE")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo(finished.id.toString())

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/MDI?status=PROCESSING")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo(processing.id.toString())
    }

    @Test
    fun `drills into a single upload returning per-cell results with values changed`() {
      val row = CellCertificateUploadLocation(
        locationKey = "MDI-Z-1-001",
        maxCapacity = 2,
        workingCapacity = 1,
        certifiedNormalAccommodation = 2,
      ).apply {
        recordPreviousValues(
          previousMaxCapacity = 2,
          previousWorkingCapacity = 2,
          previousCertifiedNormalAccommodation = 2,
          previousCellMark = null,
          previousInCellSanitation = null,
        )
        markProcessed(LocalDateTime.now(clock))
      }
      val upload = saveUpload("MDI", CellCertificateUploadStatus.FINISHED, daysAgo = 0, rows = listOf(row))

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/upload/${upload.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(upload.id.toString())
        .jsonPath("$.status").isEqualTo("FINISHED")
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].locationKey").isEqualTo("MDI-Z-1-001")
        .jsonPath("$.locations[0].status").isEqualTo("PROCESSED")
        .jsonPath("$.locations[0].previousWorkingCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(1)
    }

    @Test
    fun `returns 404 when the upload does not exist`() {
      webTestClient.get().uri("/locations/bulk/update-cell-certificate/upload/${java.util.UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `list endpoint is forbidden without the role`() {
      webTestClient.get().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }
  }
}
