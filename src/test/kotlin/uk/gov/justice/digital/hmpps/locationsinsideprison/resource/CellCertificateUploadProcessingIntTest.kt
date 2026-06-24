package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadProcessingService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CellCertificateUploadProcessingIntTest : CommonDataTestBase() {

  @Autowired
  lateinit var cellCertificateUploadRepository: CellCertificateUploadRepository

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var transactionManager: PlatformTransactionManager

  @Autowired
  lateinit var processingService: CellCertificateUploadProcessingService

  private val uploadQueue by lazy { hmppsQueueService.findByQueueId(UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY) as HmppsQueue }

  @BeforeEach
  fun cleanUp() {
    uploadQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(uploadQueue.queueUrl).build())
    cellCertificateUploadRepository.deleteAll()
  }

  @Test
  fun `processes an upload, identifies INACTIVE_TEMP cells and generates a current certificate`() {
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), false)

    val body = jsonString(
      UpdateCapacityRequest(
        locations = mapOf(
          // active cell, working capacity reduced 2 -> 1
          cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
          // active cell, no change
          cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
          // temporarily inactive cell that keeps a working capacity -> must become INACTIVE_TEMP
          inactiveCellB3001.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
        ),
      ),
    )

    webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted

    // wait for the asynchronous processing to finish
    await untilAsserted {
      val upload = cellCertificateUploadRepository.findAll().firstOrNull()
      assertThat(upload?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      assertThat(upload.cellCertificateId).isNotNull()
      assertThat(upload.processedRecords).isEqualTo(2) // cell1 changed + inactive cell flagged
      assertThat(upload.skippedRecords).isEqualTo(1) // cell2 unchanged
      assertThat(upload.failedRecords).isEqualTo(0)

      val rows = upload.locations.associateBy { it.locationKey }
      with(rows.getValue(cell1.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(previousWorkingCapacity).isEqualTo(2)
        assertThat(workingCapacity).isEqualTo(1)
      }
      with(rows.getValue(cell2.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.SKIPPED)
      }
      with(rows.getValue(inactiveCellB3001.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
      }
    }

    // the temporarily inactive cell is now flagged INACTIVE_TEMP
    val reloadedInactiveCell = cellRepository.findById(inactiveCellB3001.id!!).get()
    assertThat(reloadedInactiveCell.isShortTermInactive()).isTrue()

    // a current certificate was generated that keeps the temp-inactive cell's working capacity
    val certificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")
    assertThat(certificate).isNotNull
    val inactiveCellOnCert = certificate!!.findLocationInCertificate(inactiveCellB3001.getPathHierarchy())
    assertThat(inactiveCellOnCert?.workingCapacity).isEqualTo(2)
    // total working capacity = cell1 (1) + cell2 (2) + temp-inactive cell (2)
    assertThat(certificate.totalWorkingCapacity).isEqualTo(5)

    // a LOCATION_AMENDED event is raised for the cell whose capacity changed (cell1) and its parents.
    // cell2 (unchanged) and inactiveCellB3001 (INACTIVE_TEMP flag only, no capacity value change) raise none.
    getDomainEvents(3).let { events ->
      assertThat(events.map { it.eventType to it.additionalInformation?.key }).containsExactlyInAnyOrder(
        "location.inside.prison.amended" to cell1.getKey(),
        "location.inside.prison.amended" to landingZ1.getKey(),
        "location.inside.prison.amended" to wingZ.getKey(),
      )
    }
  }

  @Test
  fun `re-processing a finished upload does not create a second current certificate`() {
    val body = jsonString(
      UpdateCapacityRequest(
        locations = mapOf(
          cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
        ),
      ),
    )

    webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted

    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().firstOrNull()?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }
    val uploadId = cellCertificateUploadRepository.findAll().first().id!!

    // re-send the same START_PROCESSING message - the status guard must prevent re-processing
    processingService.process(uploadId)

    assertThat(cellCertificateRepository.findByPrisonIdOrderByApprovedDateDesc("MDI").filter { it.toDto().current }).hasSize(1)
  }

  @Test
  fun `concurrent processing of the same upload creates only one certificate`() {
    // Build a PENDING upload directly (a single no-change row) without sending an SQS message, so the test
    // controls when processing is invoked. cell2 already has 2/2/2, so the row is a no-op (SKIPPED); this
    // isolates the test to the claim/finish concurrency rather than capacity validation.
    val uploadId = TransactionTemplate(transactionManager).execute {
      val upload = CellCertificateUpload(
        prisonId = "MDI",
        requestedBy = "TEST_USER",
        requestedDate = LocalDateTime.now(),
        totalRecords = 1,
      ).apply {
        addLocation(
          CellCertificateUploadLocation(
            locationKey = cell2.getKey(),
            maxCapacity = 2,
            workingCapacity = 2,
            certifiedNormalAccommodation = 2,
          ),
        )
      }
      cellCertificateUploadRepository.save(upload).id!!
    }!!

    // Simulate the same SQS message being redelivered to several pods at once. Without the pessimistic
    // claim each run would create its own certificate (the bug); the lock must serialise them to one.
    val threadCount = 3
    val startLatch = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(threadCount)
    try {
      val futures = (1..threadCount).map {
        executor.submit {
          startLatch.await()
          processingService.process(uploadId)
        }
      }
      startLatch.countDown()
      futures.forEach { it.get(30, TimeUnit.SECONDS) }
    } finally {
      executor.shutdownNow()
    }

    TransactionTemplate(transactionManager).execute {
      assertThat(cellCertificateUploadRepository.findById(uploadId).get().status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }
    assertThat(cellCertificateRepository.findByPrisonIdOrderByApprovedDateDesc("MDI")).hasSize(1)
  }

  @Test
  fun `a location that cannot be found is recorded as a failure`() {
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), false)

    val body = jsonString(
      UpdateCapacityRequest(
        locations = mapOf(
          // existing cell, working capacity reduced 2 -> 1
          cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
          // non-existent location -> must be recorded as FAILED, not skipped
          "MDI-Z-9-999" to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 1),
        ),
      ),
    )

    webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted

    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().firstOrNull()?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      assertThat(upload.processedRecords).isEqualTo(1) // cell1 changed
      assertThat(upload.skippedRecords).isEqualTo(0)
      assertThat(upload.failedRecords).isEqualTo(1) // the missing location
      with(upload.locations.associateBy { it.locationKey }.getValue("MDI-Z-9-999")) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.FAILED)
        assertThat(message).isEqualTo("Location not found")
      }
    }
  }

  @Test
  fun `a second upload regenerates the certificate from the updated capacities`() {
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), false)

    // first ingestion: reduce cell1 working capacity 2 -> 1, creating the current certificate
    postCellCertificateUpdate(
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2)),
    )
    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().count { it.status == CellCertificateUploadStatus.FINISHED }).isEqualTo(1)
    }
    assertThat(
      cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")!!
        .findLocationInCertificate(cell1.getPathHierarchy())?.workingCapacity,
    ).isEqualTo(1)

    // second ingestion: raise cell1 working capacity 1 -> 2. The regenerated certificate must reflect this,
    // not clone the stale value from the previous certificate.
    postCellCertificateUpdate(
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2)),
    )
    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().count { it.status == CellCertificateUploadStatus.FINISHED }).isEqualTo(2)
    }

    val certificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")
    assertThat(certificate).isNotNull
    val cell1OnCert = certificate!!.findLocationInCertificate(cell1.getPathHierarchy())
    assertThat(cell1OnCert?.workingCapacity).isEqualTo(2)
    assertThat(cell1OnCert?.maxCapacity).isEqualTo(2)
  }

  @Test
  fun `working capacity cannot be set below occupancy for a normal accommodation cell but is allowed for non-normal`() {
    // cell1 (NORMAL_ACCOMMODATION) and cell2 (CARE_AND_SEPARATION) each hold 2 prisoners
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), true, numberOfPrisonersInCell = 2)
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell2.getPathHierarchy()), true, numberOfPrisonersInCell = 2)

    postCellCertificateUpdate(
      mapOf(
        // normal cell: working capacity 1 < occupancy 2 -> must FAIL (max 2 is still >= occupancy)
        cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
        // care & separation cell: working capacity 0 with occupancy 2 -> allowed (only max capacity is checked)
        cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 0, certifiedNormalAccommodation = 0),
      ),
    )

    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().firstOrNull()?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }

    TransactionTemplate(transactionManager).execute {
      val rows = cellCertificateUploadRepository.findAll().first().locations.associateBy { it.locationKey }
      with(rows.getValue(cell1.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.FAILED)
        assertThat(message).contains("Working capacity (1) cannot be decreased below current cell occupancy (2)")
      }
      with(rows.getValue(cell2.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
      }
    }
  }

  private fun postCellCertificateUpdate(locations: Map<String, CellCapacityUpdateDetail>) {
    webTestClient.post().uri("/locations/bulk/update-cell-certificate/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(UpdateCapacityRequest(locations = locations)))
      .exchange()
      .expectStatus().isAccepted
  }

  @Test
  fun `running counts are incremented atomically per row`() {
    val uploadId = TransactionTemplate(transactionManager).execute {
      cellCertificateUploadRepository.save(
        CellCertificateUpload(
          prisonId = "MDI",
          requestedBy = "TEST_USER",
          requestedDate = LocalDateTime.now(),
          totalRecords = 4,
        ),
      ).id!!
    }!!

    // each increment mirrors what processRow commits as a row completes - visible to a GET refresh mid-upload
    TransactionTemplate(transactionManager).execute {
      cellCertificateUploadRepository.incrementProcessedRecords(uploadId)
      cellCertificateUploadRepository.incrementProcessedRecords(uploadId)
      cellCertificateUploadRepository.incrementSkippedRecords(uploadId)
      cellCertificateUploadRepository.incrementFailedRecords(uploadId)
    }

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findById(uploadId).get()
      assertThat(upload.processedRecords).isEqualTo(2)
      assertThat(upload.skippedRecords).isEqualTo(1)
      assertThat(upload.failedRecords).isEqualTo(1)
    }
  }
}
