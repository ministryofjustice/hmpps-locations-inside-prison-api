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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadProcessingService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

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
}
