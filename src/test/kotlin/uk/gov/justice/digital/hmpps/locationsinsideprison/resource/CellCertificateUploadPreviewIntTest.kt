package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateTotalsDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationHistoryRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadProcessingService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Duration
import java.util.UUID

class CellCertificateUploadPreviewIntTest : CommonDataTestBase() {

  @Autowired
  lateinit var cellCertificateUploadRepository: CellCertificateUploadRepository

  @Autowired
  lateinit var locationHistoryRepository: LocationHistoryRepository

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var processingService: CellCertificateUploadProcessingService

  private val uploadQueue by lazy { hmppsQueueService.findByQueueId(UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY) as HmppsQueue }

  /** A cell arriving from NOMIS: it holds a CNA but has never held a working capacity. */
  private lateinit var cellWithoutWorkingCapacity: Cell

  /** A cell converted to an office: it holds no capacity (MAPA-413). */
  private lateinit var office: Cell

  @BeforeEach
  fun setUpPreviewData() {
    uploadQueue.sqsClient.purgeAndAwaitEmpty(uploadQueue.queueUrl)
    cellCertificateUploadRepository.deleteAll()

    cellWithoutWorkingCapacity = repository.save(
      buildCell(
        pathHierarchy = "Z-2-010",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 0, certifiedNormalAccommodation = 2),
        linkedTransaction = linkedTransaction,
      ),
    )
    repository.save(landingZ2.addChildLocation(cellWithoutWorkingCapacity))

    office = repository.save(buildCell(pathHierarchy = "Z-2-020", linkedTransaction = linkedTransaction)) as Cell
    repository.save(landingZ2.addChildLocation(office))
    office.convertToNonResidentialCell(ConvertedCellType.OFFICE, null, EXPECTED_USERNAME, clock, linkedTransaction)
    office = repository.save(office) as Cell

    listOf(cell1, inactiveCellB3001, cellWithoutWorkingCapacity).forEach {
      prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(it.getPathHierarchy()), false)
    }
    // cell2 holds two people, so a max capacity of one cannot be applied to it
    prisonerSearchMockServer.stubSearchByLocations("MDI", listOf(cell2.getPathHierarchy()), true, numberOfPrisonersInCell = 2)
  }

  /**
   * A file that exercises every rule: a max capacity raised with the working capacity kept, a new cell mark and
   * sanitation (cell1); a max capacity below occupancy (cell2); a location that does not exist; a temporarily
   * inactive cell that keeps a working capacity; and a cell that takes its working capacity from the certificate.
   * Any other certifiable cell is left off the file.
   */
  private fun changesFile() = mapOf(
    cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 3, workingCapacity = 1, certifiedNormalAccommodation = 2, cellMark = "Z1-NEW", inCellSanitation = true),
    cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 1, workingCapacity = 1, certifiedNormalAccommodation = 1),
    "MDI-Z-1-999" to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
    inactiveCellB3001.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
    cellWithoutWorkingCapacity.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
    // a converted cell given a capacity: flagged, nothing applied, certified at 0
    office.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
  )

  @Test
  fun `a preview works out every row but changes nothing`() {
    val cellsBefore = cellStates()
    val historyBefore = locationHistoryRepository.count()
    val linkedTransactionsBefore = linkedTransactionRepository.count()
    val approvalRequestsBefore = certificationApprovalRequestRepository.count()
    val certificatesBefore = cellCertificateRepository.count()

    val previewId = post("/locations/bulk/update-cell-certificate/MDI/preview", changesFile()).id
    awaitFinished(previewId)

    TransactionTemplate(transactionManager).execute {
      val preview = cellCertificateUploadRepository.findById(previewId).get()
      val rows = preview.locations.associateBy { it.locationKey }

      // every row was worked out as an import would work it out
      assertThat(rows.values).noneMatch { it.status == CellCertificateUploadLocationStatus.PENDING }
      with(rows.getValue(cell1.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        assertThat(appliedMaxCapacity).isEqualTo(3)
        assertThat(appliedWorkingCapacity).isEqualTo(2)
        assertThat(workingCapacityMismatch).isTrue()
        assertThat(previousCellMark).isEqualTo("Z1-#001")
      }
      assertThat(rows.getValue("MDI-Z-1-999").status).isEqualTo(CellCertificateUploadLocationStatus.FAILED)
      assertThat(rows.getValue("MDI-Z-1-999").message).isEqualTo(CellCertificateUploadProcessingService.LOCATION_NOT_FOUND_MESSAGE)
      with(rows.getValue(cellWithoutWorkingCapacity.getKey())) {
        assertThat(appliedWorkingCapacity).isEqualTo(2)
        assertThat(message).isEqualTo(CellCertificateUploadProcessingService.WORKING_CAPACITY_TAKEN_FROM_CERTIFICATE_MESSAGE)
      }
      with(rows.getValue(inactiveCellB3001.getKey())) {
        assertThat(status).isEqualTo(CellCertificateUploadLocationStatus.PROCESSED)
        // kept although the rest of the row's work was rolled back
        assertThat(inactive).isTrue()
        assertThat(deactivatedReason).isEqualTo(DeactivatedReason.DAMAGED)
        assertThat(specialistCellTypes).isEqualTo("ACCESSIBLE_CELL")
      }
      assertThat(rows.getValue(cell1.getKey()).inactive).isFalse()

      assertThat(preview.processedRecords + preview.skippedRecords + preview.failedRecords).isEqualTo(6)
      assertThat(preview.failedRecords).isEqualTo(1)
      assertThat(preview.discrepancyRecords).isGreaterThan(0)
      assertThat(preview.notOnCertificateRecords).isEqualTo(preview.locationsNotOnCertificate.size)

      // the prison had no certificate, so there is nothing to compare with, but the new one's totals are known
      assertThat(preview.currentWorkingCapacity).isNull()
      assertThat(preview.projectedWorkingCapacity).isNotNull()
      assertThat(preview.cellCertificateId).isNull()
      assertThat(preview.certificationApprovalRequestId).isNull()
    }

    // ...and none of it happened
    assertThat(cellStates()).isEqualTo(cellsBefore)
    assertThat(locationHistoryRepository.count()).isEqualTo(historyBefore)
    assertThat(linkedTransactionRepository.count()).isEqualTo(linkedTransactionsBefore)
    assertThat(certificationApprovalRequestRepository.count()).isEqualTo(approvalRequestsBefore)
    assertThat(cellCertificateRepository.count()).isEqualTo(certificatesBefore)
    Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until { getNumberOfMessagesCurrentlyOnQueue() == 0 }
  }

  @Test
  fun `a preview reports exactly what the import of the same file then does`() {
    // a current certificate to compare against, from a file that changes nothing
    val baselineId = post(
      "/locations/bulk/update-cell-certificate/MDI",
      mapOf(
        cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
        cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
      ),
    ).id
    awaitFinished(baselineId)
    val baseline = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")!!

    val previewId = post("/locations/bulk/update-cell-certificate/MDI/preview", changesFile()).id
    awaitFinished(previewId)
    val preview = results(previewId)

    // the preview compares against the certificate that was current when it ran
    assertThat(preview.currentCertificateTotals).isEqualTo(
      CellCertificateTotalsDto(baseline.totalMaxCapacity, baseline.totalWorkingCapacity, baseline.totalCertifiedNormalAccommodation),
    )
    assertThat(cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")!!.id).isEqualTo(baseline.id)

    val importId = post("/locations/bulk/update-cell-certificate/upload/$previewId/import", null).id
    awaitFinished(importId)
    val import = results(importId)

    assertThat(import.mode).isEqualTo(CellCertificateUploadMode.IMPORT)
    assertThat(import.previewUploadId).isEqualTo(previewId)

    // guard against comparing two empty results: the preview covered every row, and the import really changed cells
    assertThat(preview.locations).hasSize(6)
    assertThat(preview.locations!!.map { it.status }).contains(
      CellCertificateUploadLocationStatus.PROCESSED,
      CellCertificateUploadLocationStatus.FAILED,
    )
    TransactionTemplate(transactionManager).executeWithoutResult {
      assertThat(cellRepository.findById(cell1.id!!).get().getMaxCapacity()).isEqualTo(3)
    }

    // row by row, the import did exactly what the preview said it would
    assertThat(import.locations!!.map { it.copy(processedDate = null) })
      .containsExactlyInAnyOrderElementsOf(preview.locations!!.map { it.copy(processedDate = null) })
    assertThat(import.locationsNotOnCertificate).containsExactlyInAnyOrderElementsOf(preview.locationsNotOnCertificate)
    assertThat(import.processedRecords).isEqualTo(preview.processedRecords)
    assertThat(import.skippedRecords).isEqualTo(preview.skippedRecords)
    assertThat(import.failedRecords).isEqualTo(preview.failedRecords)
    assertThat(import.discrepancyRecords).isEqualTo(preview.discrepancyRecords)

    // and the certificate it created has the totals the preview projected
    val certificate = cellCertificateRepository.findById(import.cellCertificateId!!).get()
    assertThat(preview.projectedCertificateTotals).isEqualTo(
      CellCertificateTotalsDto(certificate.totalMaxCapacity, certificate.totalWorkingCapacity, certificate.totalCertifiedNormalAccommodation),
    )
  }

  @Test
  fun `a cell left off the file keeps its certified values, in the preview and in the import`() {
    // cell2 is certified at working capacity 1 while the cell keeps its own 2
    val baselineId = post(
      "/locations/bulk/update-cell-certificate/MDI",
      mapOf(
        cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
        cell2.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 1, certifiedNormalAccommodation = 2),
      ),
    ).id
    awaitFinished(baselineId)
    val baseline = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue("MDI")!!

    // a file listing only cell1, at the values it already has
    val previewId = post(
      "/locations/bulk/update-cell-certificate/MDI/preview",
      mapOf(cell1.getKey() to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2)),
    ).id
    awaitFinished(previewId)
    val preview = results(previewId)

    // cell2 is carried forward at 1, not re-certified at the 2 it holds, so the totals do not move
    assertThat(preview.locationsNotOnCertificate!!.first { it.locationKey == cell2.getKey() }.workingCapacity).isEqualTo(1)
    assertThat(preview.projectedCertificateTotals).isEqualTo(preview.currentCertificateTotals)
    assertThat(preview.currentCertificateTotals!!.workingCapacity).isEqualTo(baseline.totalWorkingCapacity)

    val importId = post("/locations/bulk/update-cell-certificate/upload/$previewId/import", null).id
    awaitFinished(importId)
    val import = results(importId)

    assertThat(import.locationsNotOnCertificate).containsExactlyInAnyOrderElementsOf(preview.locationsNotOnCertificate)
    val certificate = cellCertificateRepository.findById(import.cellCertificateId!!).get()
    assertThat(certificate.findLocationInCertificate(cell2.getPathHierarchy())!!.workingCapacity).isEqualTo(1)
    assertThat(preview.projectedCertificateTotals).isEqualTo(
      CellCertificateTotalsDto(certificate.totalMaxCapacity, certificate.totalWorkingCapacity, certificate.totalCertifiedNormalAccommodation),
    )
  }

  @Test
  fun `a preview delivered twice is only worked out once`() {
    val previewId = post("/locations/bulk/update-cell-certificate/MDI/preview", changesFile()).id
    awaitFinished(previewId)
    val first = results(previewId)

    processingService.process(previewId)

    val second = results(previewId)
    assertThat(second.locations).isEqualTo(first.locations)
    assertThat(second.endTime).isEqualTo(first.endTime)
    assertThat(second.processedRecords).isEqualTo(first.processedRecords)
  }

  private fun post(uri: String, locations: Map<String, CellCapacityUpdateDetail>?): CellCertificateUploadDto = webTestClient.post().uri(uri)
    .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
    .header("Content-Type", "application/json")
    .let { request -> locations?.let { request.bodyValue(jsonString(UpdateCapacityRequest(locations = it))) } ?: request }
    .exchange()
    .expectStatus().isAccepted
    .expectBody(CellCertificateUploadDto::class.java)
    .returnResult().responseBody!!

  private fun results(id: UUID): CellCertificateUploadDto = webTestClient.get().uri("/locations/bulk/update-cell-certificate/upload/$id")
    .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS")))
    .exchange()
    .expectStatus().isOk
    .expectBody(CellCertificateUploadDto::class.java)
    .returnResult().responseBody!!

  private fun awaitFinished(id: UUID) {
    await untilAsserted {
      assertThat(cellCertificateUploadRepository.findById(id).get().status).isEqualTo(CellCertificateUploadStatus.FINISHED)
    }
  }

  private data class CellState(
    val maxCapacity: Int?,
    val workingCapacity: Int?,
    val certifiedNormalAccommodation: Int?,
    val cellMark: String?,
    val inCellSanitation: Boolean?,
    val shortTermInactive: Boolean,
  )

  /** Everything a row in the file could change, for every cell in the file. */
  private fun cellStates(): Map<String, CellState> = TransactionTemplate(transactionManager).execute {
    listOf(cell1, cell2, inactiveCellB3001, cellWithoutWorkingCapacity).associate { cell ->
      with(cellRepository.findById(cell.id!!).get()) {
        getKey() to CellState(
          maxCapacity = getMaxCapacity(),
          workingCapacity = getCurrentlyHeldWorkingCapacity(),
          certifiedNormalAccommodation = getCertifiedNormalAccommodation(),
          cellMark = getDoorCellMark(),
          inCellSanitation = getSanitationOfCell(),
          shortTermInactive = isShortTermInactive(),
        )
      }
    }
  }!!
}
