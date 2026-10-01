package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadListenerService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.util.UUID

class CellCertificateUploadResourceIntTest : CommonDataTestBase() {

  @Autowired
  lateinit var cellCertificateUploadRepository: CellCertificateUploadRepository

  @MockitoSpyBean
  lateinit var cellCertificateUploadListenerService: CellCertificateUploadListenerService

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
      // the bulk processing checks occupancy before changing capacities - both cells are empty
      prisonerSearchMockServer.stubSearchByLocations("MDI", listOf("Z-1-001"), false)
      prisonerSearchMockServer.stubSearchByLocations("MDI", listOf("Z-1-002"), false)

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
    fun `returns 400 when a row certifies a working capacity above its max capacity`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(
          jsonString(
            UpdateCapacityRequest(
              locations = mapOf(
                "MDI-Z-1-001" to CellCapacityUpdateDetail(maxCapacity = 1, workingCapacity = 2, certifiedNormalAccommodation = 1),
              ),
            ),
          ),
        )
        .exchange()
        .expectStatus().isBadRequest

      assertThat(cellCertificateUploadRepository.findAll()).isEmpty()
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
    private fun saveUpload(
      prisonId: String,
      status: CellCertificateUploadStatus,
      daysAgo: Long,
      rows: List<CellCertificateUploadLocation> = emptyList(),
      discrepancyRecords: Int = 0,
    ) = cellCertificateUploadRepository.saveAndFlush(
      CellCertificateUpload(
        prisonId = prisonId,
        status = status,
        requestedBy = EXPECTED_USERNAME,
        requestedDate = LocalDateTime.now(clock).minusDays(daysAgo),
        totalRecords = rows.size,
        discrepancyRecords = discrepancyRecords,
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
          appliedMaxCapacity = 2,
          appliedWorkingCapacity = 2,
        )
        recordDiscrepancy(
          workingCapacityMismatch = true,
          maxCapacityMismatch = false,
          certifiedNormalAccommodationMismatch = false,
        )
        markProcessed(LocalDateTime.now(clock))
      }
      val upload = saveUpload("MDI", CellCertificateUploadStatus.FINISHED, daysAgo = 0, rows = listOf(row), discrepancyRecords = 1)

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
        .jsonPath("$.locations[0].appliedWorkingCapacity").isEqualTo(2)
        .jsonPath("$.locations[0].workingCapacity").isEqualTo(1)
        .jsonPath("$.discrepancyRecords").isEqualTo(1)
        .jsonPath("$.locations[0].workingCapacityMismatch").isEqualTo(true)
        .jsonPath("$.locations[0].maxCapacityMismatch").isEqualTo(false)
        .jsonPath("$.locations[0].certifiedNormalAccommodationMismatch").isEqualTo(false)
    }

    @Test
    fun `finds the upload behind an approval request`() {
      val approvalRequestId = java.util.UUID.randomUUID()
      val row = CellCertificateUploadLocation(
        locationKey = "MDI-Z-1-001",
        maxCapacity = 2,
        workingCapacity = 1,
        certifiedNormalAccommodation = 2,
      ).apply { markProcessed(LocalDateTime.now(clock)) }
      val upload = saveUpload("MDI", CellCertificateUploadStatus.FINISHED, daysAgo = 0, rows = listOf(row))
        .also {
          it.certificationApprovalRequestId = approvalRequestId
          cellCertificateUploadRepository.saveAndFlush(it)
        }

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/by-approval-request/$approvalRequestId")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(upload.id.toString())
        .jsonPath("$.certificationApprovalRequestId").isEqualTo(approvalRequestId.toString())
        .jsonPath("$.locations.length()").isEqualTo(1)
        .jsonPath("$.locations[0].locationKey").isEqualTo("MDI-Z-1-001")
    }

    @Test
    fun `returns 404 when no upload was raised by the approval request`() {
      webTestClient.get().uri("/locations/bulk/update-cell-certificate/by-approval-request/${java.util.UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CERTIFICATION")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `by-approval-request is forbidden without a suitable role`() {
      webTestClient.get().uri("/locations/bulk/update-cell-certificate/by-approval-request/${java.util.UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
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

  @DisplayName("Previews")
  @Nested
  inner class Preview {
    private fun saveUpload(
      mode: CellCertificateUploadMode,
      status: CellCertificateUploadStatus,
      requestedDate: LocalDateTime = LocalDateTime.now(clock),
      endTime: LocalDateTime? = null,
      previewUploadId: UUID? = null,
      rows: List<CellCertificateUploadLocation> = emptyList(),
    ) = cellCertificateUploadRepository.saveAndFlush(
      CellCertificateUpload(
        prisonId = "MDI",
        status = status,
        mode = mode,
        previewUploadId = previewUploadId,
        requestedBy = EXPECTED_USERNAME,
        requestedDate = requestedDate,
        endTime = endTime,
        totalRecords = rows.size,
      ).apply { rows.forEach { addLocation(it) } },
    )

    private fun stubCellsEmpty() {
      prisonerSearchMockServer.stubSearchByLocations("MDI", listOf("Z-1-001"), false)
      prisonerSearchMockServer.stubSearchByLocations("MDI", listOf("Z-1-002"), false)
    }

    private fun postPreview() = webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI/preview")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(requestBody())
      .exchange()

    private fun postContinue(previewId: UUID?, user: String = EXPECTED_USERNAME) = webTestClient.post().uri("/locations/bulk/update-cell-certificate/upload/$previewId/import")
      .headers(setAuthorisation(user = user, roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .exchange()

    private fun awaitFinished(id: UUID) {
      await untilAsserted {
        assertThat(cellCertificateUploadRepository.findById(id).get().status).isEqualTo(CellCertificateUploadStatus.FINISHED)
      }
    }

    @Test
    fun `preview is forbidden without write scope`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI/preview")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `continue is forbidden without write scope`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/upload/${UUID.randomUUID()}/import")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `stores a preview, returns 202 and queues it as a preview`() {
      postPreview()
        .expectStatus().isAccepted
        .expectBody()
        .jsonPath("$.mode").isEqualTo("PREVIEW")
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.totalRecords").isEqualTo(2)

      await untilAsserted {
        verify(cellCertificateUploadListenerService).onEventReceived(argThat { contains("START_PREVIEW") })
      }
      val preview = cellCertificateUploadRepository.findAll().single()
      awaitFinished(preview.id!!)

      with(cellCertificateUploadRepository.findById(preview.id!!).get()) {
        assertThat(mode).isEqualTo(CellCertificateUploadMode.PREVIEW)
        // a preview never raises an approval request or creates a certificate
        assertThat(cellCertificateId).isNull()
        assertThat(certificationApprovalRequestId).isNull()
      }
    }

    @Test
    fun `returns 400 for a preview when prison requires approval and reason for change is missing`() {
      webTestClient.post().uri("/locations/bulk/update-cell-certificate/LEI/preview")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `a preview can be requested while an import is in progress`() {
      saveUpload(CellCertificateUploadMode.IMPORT, CellCertificateUploadStatus.STARTED)

      postPreview().expectStatus().isAccepted
    }

    @Test
    fun `an import can be requested while a preview is in progress`() {
      stubCellsEmpty()
      saveUpload(CellCertificateUploadMode.PREVIEW, CellCertificateUploadStatus.STARTED)

      val import = webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
        .header("Content-Type", "application/json")
        .bodyValue(requestBody())
        .exchange()
        .expectStatus().isAccepted
        .expectBody(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto::class.java)
        .returnResult().responseBody!!

      assertThat(import.mode).isEqualTo(CellCertificateUploadMode.IMPORT)
      awaitFinished(import.id)
    }

    @Test
    fun `continuing a finished preview copies its uploaded rows into a new queued import`() {
      stubCellsEmpty()
      val previewedRow = CellCertificateUploadLocation(
        locationKey = "MDI-Z-1-001",
        maxCapacity = 3,
        workingCapacity = 1,
        certifiedNormalAccommodation = 2,
        cellMark = "A001",
        inCellSanitation = true,
      ).apply {
        recordDiscrepancy(workingCapacityMismatch = true, maxCapacityMismatch = false, certifiedNormalAccommodationMismatch = false)
        markProcessed(LocalDateTime.now(clock))
      }
      val preview = saveUpload(
        CellCertificateUploadMode.PREVIEW,
        CellCertificateUploadStatus.FINISHED,
        endTime = LocalDateTime.now(clock),
        rows = listOf(previewedRow),
      )

      // the import is recorded against whoever continues it, not whoever ran the preview
      val import = postContinue(preview.id, user = "CONTINUING_USER")
        .expectStatus().isAccepted
        .expectBody(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto::class.java)
        .returnResult().responseBody!!

      assertThat(import.mode).isEqualTo(CellCertificateUploadMode.IMPORT)
      assertThat(import.previewUploadId).isEqualTo(preview.id)
      assertThat(import.status).isEqualTo(CellCertificateUploadStatus.PENDING)
      assertThat(import.requestedBy).isEqualTo("CONTINUING_USER")
      assertThat(import.totalRecords).isEqualTo(1)

      // only the uploaded values are copied - the import works out its own outcome
      TransactionTemplate(transactionManager).execute {
        val copiedRow = cellCertificateUploadRepository.findById(import.id).get().locations.single()
        assertThat(copiedRow.id).isNotEqualTo(previewedRow.id)
        assertThat(copiedRow.locationKey).isEqualTo("MDI-Z-1-001")
        assertThat(copiedRow.maxCapacity).isEqualTo(3)
        assertThat(copiedRow.workingCapacity).isEqualTo(1)
        assertThat(copiedRow.certifiedNormalAccommodation).isEqualTo(2)
        assertThat(copiedRow.cellMark).isEqualTo("A001")
        assertThat(copiedRow.inCellSanitation).isTrue()
      }

      await untilAsserted {
        verify(cellCertificateUploadListenerService).onEventReceived(argThat { contains("START_PROCESSING") && contains(import.id.toString()) })
      }
      awaitFinished(import.id)

      // the preview now points at the import it became
      webTestClient.get().uri("/locations/bulk/update-cell-certificate/upload/${preview.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.mode").isEqualTo("PREVIEW")
        .jsonPath("$.continuedAsUploadId").isEqualTo(import.id.toString())

      webTestClient.get().uri("/locations/bulk/update-cell-certificate/MDI")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[?(@.id == '${preview.id}')].mode").isEqualTo("PREVIEW")
        .jsonPath("$[?(@.id == '${preview.id}')].continuedAsUploadId").isEqualTo(import.id.toString())
        .jsonPath("$[?(@.id == '${import.id}')].mode").isEqualTo("IMPORT")
        .jsonPath("$[?(@.id == '${import.id}')].previewUploadId").isEqualTo(preview.id.toString())
    }

    @Test
    fun `returns 404 when continuing a preview that does not exist`() {
      postContinue(UUID.randomUUID()).expectStatus().isNotFound
    }

    @Test
    fun `returns 404 when continuing an import rather than a preview`() {
      val import = saveUpload(CellCertificateUploadMode.IMPORT, CellCertificateUploadStatus.FINISHED, endTime = LocalDateTime.now(clock))

      postContinue(import.id).expectStatus().isNotFound
    }

    @Test
    fun `returns 400 when continuing a preview that has not finished`() {
      val preview = saveUpload(CellCertificateUploadMode.PREVIEW, CellCertificateUploadStatus.STARTED)

      postContinue(preview.id)
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(147)
    }

    @Test
    fun `returns 409 when the preview has already been continued`() {
      val preview = saveUpload(CellCertificateUploadMode.PREVIEW, CellCertificateUploadStatus.FINISHED, endTime = LocalDateTime.now(clock))
      saveUpload(CellCertificateUploadMode.IMPORT, CellCertificateUploadStatus.FINISHED, endTime = LocalDateTime.now(clock), previewUploadId = preview.id)

      postContinue(preview.id)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(148)
    }

    @Test
    fun `returns 409 when an import is already in progress for the prison`() {
      val preview = saveUpload(CellCertificateUploadMode.PREVIEW, CellCertificateUploadStatus.FINISHED, endTime = LocalDateTime.now(clock))
      saveUpload(CellCertificateUploadMode.IMPORT, CellCertificateUploadStatus.STARTED)

      postContinue(preview.id)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(139)
    }

    @Test
    fun `returns 409 when an import has finished for the prison since the preview was run`() {
      val previewRequested = LocalDateTime.now(clock).minusHours(2)
      val preview = saveUpload(
        CellCertificateUploadMode.PREVIEW,
        CellCertificateUploadStatus.FINISHED,
        requestedDate = previewRequested,
        endTime = previewRequested.plusMinutes(5),
      )
      saveUpload(
        CellCertificateUploadMode.IMPORT,
        CellCertificateUploadStatus.FINISHED,
        requestedDate = previewRequested.plusMinutes(30),
        endTime = previewRequested.plusMinutes(40),
      )

      postContinue(preview.id)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(149)
        .jsonPath("$.userMessage").isEqualTo("A cell certificate import has finished for this prison since this preview was run. Run the preview again before importing.")
    }

    @Test
    fun `an import that finished before the preview was run does not make it out of date`() {
      stubCellsEmpty()
      val previewRequested = LocalDateTime.now(clock).minusHours(2)
      saveUpload(
        CellCertificateUploadMode.IMPORT,
        CellCertificateUploadStatus.FINISHED,
        requestedDate = previewRequested.minusDays(1),
        endTime = previewRequested.minusDays(1).plusMinutes(10),
      )
      val preview = saveUpload(
        CellCertificateUploadMode.PREVIEW,
        CellCertificateUploadStatus.FINISHED,
        requestedDate = previewRequested,
        endTime = previewRequested.plusMinutes(5),
      )

      val import = postContinue(preview.id)
        .expectStatus().isAccepted
        .expectBody(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto::class.java)
        .returnResult().responseBody!!
      awaitFinished(import.id)
    }
  }
}
