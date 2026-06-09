package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadEvent
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateUploadStatusFilter
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestRequiresReasonForChangeException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateUploadAlreadyInProgressException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateUploadNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateCapacityRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class CellCertificateUploadService(
  private val cellCertificateUploadRepository: CellCertificateUploadRepository,
  private val activePrisonService: ActivePrisonService,
  private val hmppsQueueService: HmppsQueueService,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val objectMapper: ObjectMapper,
  private val clock: Clock,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId(UPDATE_CELL_CERTIFICATE_QUEUE_CONFIG_KEY) as HmppsQueue }

  /**
   * Stores an uploaded cell certificate (capacities, cell marks, sanitation) for a prison and queues it for
   * asynchronous processing. No processing is performed here - the rows are stored as PENDING and a single
   * START_PROCESSING message is sent (after commit) for a background listener to pick up.
   */
  @Transactional
  fun requestCellCertificateUpload(prisonId: String, request: UpdateCapacityRequest): CellCertificateUploadDto {
    activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    if (activePrisonService.isCertificationApprovalRequired(prisonId) && request.reasonForChange.isNullOrEmpty()) {
      throw ApprovalRequestRequiresReasonForChangeException(prisonId)
    }

    cellCertificateUploadRepository.findFirstByPrisonIdAndStatusIn(prisonId, ACTIVE_STATUSES)?.let {
      throw CellCertificateUploadAlreadyInProgressException(prisonId)
    }

    val now = LocalDateTime.now(clock)
    val upload = CellCertificateUpload(
      prisonId = prisonId,
      status = CellCertificateUploadStatus.PENDING,
      requestedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
      requestedDate = now,
      reasonForChange = request.reasonForChange,
      totalRecords = request.locations.size,
    )
    request.locations.forEach { (key, detail) ->
      upload.addLocation(
        CellCertificateUploadLocation(
          locationKey = key,
          maxCapacity = detail.maxCapacity,
          workingCapacity = detail.workingCapacity,
          certifiedNormalAccommodation = detail.certifiedNormalAccommodation,
          cellMark = detail.cellMark,
          inCellSanitation = detail.inCellSanitation,
        ),
      )
    }

    val saved = cellCertificateUploadRepository.saveAndFlush(upload)

    // The database changes MUST be committed before the message is sent so the listener can read them.
    sendStartProcessingMessageAfterCommit(saved.id!!)

    log.info("Stored cell certificate upload ${saved.id} for prison $prisonId with ${saved.totalRecords} records")
    return saved.toDto()
  }

  /**
   * Lists cell certificate uploads for a prison, most recent first, optionally filtered to those still
   * processing or those that have completed.
   */
  @Transactional(readOnly = true)
  fun getCellCertificateUploads(prisonId: String, statusFilter: CellCertificateUploadStatusFilter?): List<CellCertificateUploadDto> {
    val uploads = if (statusFilter != null) {
      cellCertificateUploadRepository.findByPrisonIdAndStatusInOrderByRequestedDateDesc(prisonId, statusFilter.statuses)
    } else {
      cellCertificateUploadRepository.findByPrisonIdOrderByRequestedDateDesc(prisonId)
    }
    return uploads.map { it.toDto() }
  }

  /**
   * Returns a single upload with its per-cell results for drill-down.
   */
  @Transactional(readOnly = true)
  fun getCellCertificateUpload(uploadId: UUID): CellCertificateUploadDto = cellCertificateUploadRepository.findById(uploadId)
    .orElseThrow { CellCertificateUploadNotFoundException(uploadId) }
    .toDto(includeLocations = true)

  private fun sendStartProcessingMessageAfterCommit(uploadId: UUID) {
    TransactionSynchronizationManager.registerSynchronization(
      object : TransactionSynchronization {
        override fun afterCommit() {
          sendStartProcessingMessage(uploadId)
        }
      },
    )
  }

  private fun sendStartProcessingMessage(uploadId: UUID) {
    val event = CellCertificateUploadEvent(eventType = CellCertificateUploadEventType.START_PROCESSING, uploadId = uploadId)
    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(objectMapper.writeValueAsString(event))
        .build(),
    ).get()
    log.info("Sent START_PROCESSING message for cell certificate upload $uploadId")
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val ACTIVE_STATUSES = listOf(CellCertificateUploadStatus.PENDING, CellCertificateUploadStatus.STARTED)
  }
}
