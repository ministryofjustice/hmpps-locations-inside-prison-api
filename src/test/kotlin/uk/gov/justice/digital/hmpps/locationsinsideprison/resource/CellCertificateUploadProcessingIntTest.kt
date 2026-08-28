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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
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
          // active cell, max capacity raised 2 -> 3 and certified working capacity reduced 2 -> 1. The max
          // capacity is applied; the working capacity is not, but the certificate still records it.
          cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 1, certifiedNormalAccommodation = 2),
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
      // the import request details page finds the ingestion through this link
      assertThat(upload.certificationApprovalRequestId).isNotNull()
      assertThat(upload.processedRecords).isEqualTo(2) // cell1 changed + inactive cell flagged
      assertThat(upload.skippedRecords).isEqualTo(1) // cell2 unchanged
      assertThat(upload.failedRecords).isEqualTo(0)
      assertThat(upload.discrepancyRecords).isEqualTo(1) // cell1 keeps a working capacity of 2, certified at 1

      val rows = upload.locations.associateBy { it.locationKey }
      with(rows.getValue(cell1.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(previousWorkingCapacity).isEqualTo(2)
        assertThat(workingCapacity).isEqualTo(1)
        assertThat(workingCapacityMismatch).isTrue()
        assertThat(maxCapacityMismatch).isFalse()
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.WORKING_CAPACITY_MISMATCH_MESSAGE)
      }
      with(rows.getValue(cell2.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.SKIPPED)
        assertThat(workingCapacityMismatch).isFalse()
      }
      with(rows.getValue(inactiveCellB3001.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
      }
    }

    // the upload applied the max capacity but left the prison's own working capacity alone
    withReloadedCell1 {
      assertThat(getMaxCapacity()).isEqualTo(3)
      assertThat(getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
    }

    // the temporarily inactive cell is now flagged INACTIVE_TEMP
    val reloadedInactiveCell = cellRepository.findById(inactiveCellB3001.id!!).get()
    assertThat(reloadedInactiveCell.isShortTermInactive()).isTrue()

    // a current certificate was generated that keeps the temp-inactive cell's working capacity
    val certificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")
    assertThat(certificate).isNotNull
    val inactiveCellOnCert = certificate!!.findLocationInCertificate(inactiveCellB3001.getPathHierarchy())
    assertThat(inactiveCellOnCert?.workingCapacity).isEqualTo(2)
    // the certificate records what the upload said, not what the location kept
    with(certificate.findLocationInCertificate(cell1.getPathHierarchy())!!) {
      assertThat(workingCapacity).isEqualTo(1)
      assertThat(maxCapacity).isEqualTo(3)
    }
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
      // cell1's only change was its working capacity, which an ingestion never applies
      assertThat(upload.processedRecords).isEqualTo(0)
      assertThat(upload.skippedRecords).isEqualTo(1)
      assertThat(upload.failedRecords).isEqualTo(1) // the missing location
      with(upload.locations.associateBy { it.locationKey }.getValue("MDI-Z-9-999")) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.FAILED)
        assertThat(message).isEqualTo("Location not found on Residential locations")
        assertThat(hasDiscrepancy()).isFalse()
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
  fun `an ingestion never moves the working capacity but the certificate takes the uploaded value`() {
    // Jira scenario 1: occupancy would allow the reduction, but ingestion must not make it anyway
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), true, numberOfPrisonersInCell = 1)

    postCellCertificateUpdate(
      mapOf(
        cell1.getKey() to CellCapacityUpdateDetail(
          maxCapacity = 2,
          workingCapacity = 1,
          certifiedNormalAccommodation = 1,
          cellMark = "Z1-NEW",
          inCellSanitation = true,
        ),
      ),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      assertThat(upload.discrepancyRecords).isEqualTo(1)
      with(upload.locations.first()) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(workingCapacityMismatch).isTrue()
        assertThat(maxCapacityMismatch).isFalse()
        assertThat(certifiedNormalAccommodationMismatch).isFalse()
        assertThat(previousWorkingCapacity).isEqualTo(2)
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.WORKING_CAPACITY_MISMATCH_MESSAGE)
      }
    }

    // the location keeps its working capacity, but everything else the upload asked for is applied
    withReloadedCell1 {
      assertThat(getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
      assertThat(getCertifiedNormalAccommodation()).isEqualTo(1)
      assertThat(getDoorCellMark()).isEqualTo("Z1-NEW")
      assertThat(getSanitationOfCell()).isTrue()
    }

    with(currentCertificateFor(cell1)) {
      assertThat(workingCapacity).isEqualTo(1)
      assertThat(maxCapacity).isEqualTo(2)
      assertThat(certifiedNormalAccommodation).isEqualTo(1)
    }
  }

  @Test
  fun `the rest of the row is still applied when the cell holds more prisoners than the uploaded working capacity`() {
    // Jira scenario 2: this row used to fail outright, discarding the CNA, cell mark and sanitation with it
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), true, numberOfPrisonersInCell = 2)

    postCellCertificateUpdate(
      mapOf(
        cell1.getKey() to CellCapacityUpdateDetail(
          maxCapacity = 2,
          workingCapacity = 1,
          certifiedNormalAccommodation = 1,
          cellMark = "Z1-NEW",
          inCellSanitation = true,
        ),
      ),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      with(cellCertificateUploadRepository.findAll().first().locations.first()) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(workingCapacityMismatch).isTrue()
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.WORKING_CAPACITY_MISMATCH_MESSAGE)
      }
    }

    withReloadedCell1 {
      assertThat(getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
      assertThat(getCertifiedNormalAccommodation()).isEqualTo(1)
      assertThat(getDoorCellMark()).isEqualTo("Z1-NEW")
      assertThat(getSanitationOfCell()).isTrue()
    }

    assertThat(currentCertificateFor(cell1).workingCapacity).isEqualTo(1)
  }

  @Test
  fun `a max capacity that would fall below occupancy is retained but still certified`() {
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell1.getPathHierarchy()), true, numberOfPrisonersInCell = 2)

    postCellCertificateUpdate(
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 1, workingCapacity = 1, certifiedNormalAccommodation = 1)),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      with(cellCertificateUploadRepository.findAll().first().locations.first()) {
        assertThat(maxCapacityMismatch).isTrue()
        assertThat(workingCapacityMismatch).isTrue()
        // the CNA the upload asked for was still applied
        assertThat(certifiedNormalAccommodationMismatch).isFalse()
      }
    }

    withReloadedCell1 {
      assertThat(getMaxCapacity()).isEqualTo(2)
      assertThat(getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
      assertThat(getCertifiedNormalAccommodation()).isEqualTo(1)
    }

    with(currentCertificateFor(cell1)) {
      assertThat(maxCapacity).isEqualTo(1)
      assertThat(workingCapacity).isEqualTo(1)
    }
  }

  @Test
  fun `cells whose working capacity already matches the uploaded value are not flagged`() {
    postCellCertificateUpdate(
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2)),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      assertThat(upload.discrepancyRecords).isEqualTo(0)
      with(upload.locations.first()) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.SKIPPED)
        assertThat(hasDiscrepancy()).isFalse()
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.NO_CHANGES_REQUIRED_MESSAGE)
      }
    }
  }

  @Test
  fun `a cell that only differs on working capacity is counted as needing review even though nothing changed`() {
    postCellCertificateUpdate(
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2)),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      assertThat(upload.processedRecords).isEqualTo(0)
      assertThat(upload.skippedRecords).isEqualTo(1)
      assertThat(upload.discrepancyRecords).isEqualTo(1)
      with(upload.locations.first()) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.SKIPPED)
        assertThat(workingCapacityMismatch).isTrue()
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.WORKING_CAPACITY_MISMATCH_MESSAGE)
      }
    }

    assertThat(currentCertificateFor(cell1).workingCapacity).isEqualTo(1)
  }

  @Test
  fun `an uploaded max capacity of zero is certified as zero and only floored to one on the location`() {
    val emptyCell = saveCellHoldingNoPrisoners("Z-2-001")
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(emptyCell.getPathHierarchy()), false)

    postCellCertificateUpdate(
      mapOf(emptyCell.getKey() to CellCapacityUpdateDetail(maxCapacity = 0, workingCapacity = 0, certifiedNormalAccommodation = 0)),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      val upload = cellCertificateUploadRepository.findAll().first()
      // rounding a max capacity of zero up to one is not something a user can resolve, so it is not a discrepancy
      assertThat(upload.discrepancyRecords).isEqualTo(0)
      with(upload.locations.first()) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(maxCapacityMismatch).isFalse()
        assertThat(hasDiscrepancy()).isFalse()
        // the report shows the value the location took, not the certified zero
        assertThat(appliedMaxCapacity).isEqualTo(1)
      }
    }

    // the location cannot hold a max capacity of zero, so it takes the floor of one ...
    TransactionTemplate(transactionManager).executeWithoutResult {
      with(cellRepository.findById(emptyCell.id!!).get()) {
        assertThat(getMaxCapacity()).isEqualTo(1)
        assertThat(getCurrentlyHeldWorkingCapacity()).isEqualTo(0)
      }
    }

    // ... but the certificate records the value the prison actually uploaded
    with(currentCertificateFor(emptyCell)) {
      assertThat(maxCapacity).isEqualTo(0)
      assertThat(workingCapacity).isEqualTo(0)
      assertThat(certifiedNormalAccommodation).isEqualTo(0)
    }
  }

  @Test
  fun `a max capacity of zero the location cannot take is still certified as zero and reported`() {
    // cell2 holds a working capacity of 2, so it cannot be reduced to the floor of one
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell2.getPathHierarchy()), false)

    postCellCertificateUpdate(
      mapOf(cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 0, workingCapacity = 0, certifiedNormalAccommodation = 0)),
    )
    awaitUploadFinished()

    TransactionTemplate(transactionManager).execute {
      with(cellCertificateUploadRepository.findAll().first().locations.first()) {
        assertThat(maxCapacityMismatch).isTrue()
        assertThat(appliedMaxCapacity).isEqualTo(2)
        // the CNA the upload asked for was still applied
        assertThat(certifiedNormalAccommodationMismatch).isFalse()
      }
    }

    TransactionTemplate(transactionManager).executeWithoutResult {
      with(cellRepository.findById(cell2.id!!).get()) {
        assertThat(getMaxCapacity()).isEqualTo(2)
        assertThat(getCertifiedNormalAccommodation()).isEqualTo(0)
      }
    }

    assertThat(currentCertificateFor(cell2).maxCapacity).isEqualTo(0)
  }

  /**
   * A cell that already holds no-one, mirroring the toilets, stores and offices prisons list on their cell
   * certificate spreadsheet with a max capacity of 0.
   */
  private fun saveCellHoldingNoPrisoners(pathHierarchy: String): Cell {
    val cell = repository.save(
      buildCell(
        pathHierarchy = pathHierarchy,
        capacity = Capacity(maxCapacity = 2, workingCapacity = 0, certifiedNormalAccommodation = 0),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
        accommodationType = AccommodationType.CARE_AND_SEPARATION,
        linkedTransaction = linkedTransaction,
      ),
    ) as Cell
    repository.save(landingZ2.addChildLocation(cell))
    return cell
  }

  /** A cell's capacity is a lazy association, so re-reading it needs an open session. */
  private fun withReloadedCell1(assertions: Cell.() -> Unit) {
    TransactionTemplate(transactionManager).executeWithoutResult {
      cellRepository.findById(cell1.id!!).get().assertions()
    }
  }

  private fun awaitUploadFinished() {
    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findAll().firstOrNull()?.status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }
  }

  private fun currentCertificateFor(cell: Cell) = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(cell.prisonId)!!
    .findLocationInCertificate(cell.getPathHierarchy())!!

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
