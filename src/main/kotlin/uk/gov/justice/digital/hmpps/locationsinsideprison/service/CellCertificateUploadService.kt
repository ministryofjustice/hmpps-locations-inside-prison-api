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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ApprovalRequestRequiresReasonForChangeException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificatePreviewAlreadyContinuedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificatePreviewNotFinishedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificatePreviewNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificatePreviewOutOfDateException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateUploadAlreadyInProgressException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateUploadForApprovalRequestNotFoundException
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
  fun requestCellCertificateUpload(prisonId: String, request: UpdateCapacityRequest): CellCertificateUploadDto = storeUpload(prisonId, request, CellCertificateUploadMode.IMPORT).toDto()

  /**
   * Stores an uploaded cell certificate as a preview and queues it. The preview is processed exactly as an
   * import would be, but every change is undone, so it reports what the import would do without changing
   * anything. Previews change nothing, so they do not wait for, or block, an import already running.
   */
  @Transactional
  fun requestCellCertificatePreview(prisonId: String, request: UpdateCapacityRequest): CellCertificateUploadDto = storeUpload(prisonId, request, CellCertificateUploadMode.PREVIEW).toDto()

  /**
   * Continues a finished preview as a real import: its uploaded rows are copied into a new import, which is
   * queued and then worked out afresh against the locations as they are when it runs. A preview can only be
   * continued once, and not after another import has finished for the prison, because what it showed may
   * no longer be what the import would do.
   */
  @Transactional
  fun continuePreview(previewId: UUID): CellCertificateUploadDto {
    val preview = cellCertificateUploadRepository.findByIdForUpdate(previewId)
      ?.takeIf { it.isPreview() }
      ?: throw CellCertificatePreviewNotFoundException(previewId)

    if (preview.status != CellCertificateUploadStatus.FINISHED) {
      throw CellCertificatePreviewNotFinishedException(previewId)
    }
    cellCertificateUploadRepository.findFirstByPreviewUploadId(previewId)?.let {
      throw CellCertificatePreviewAlreadyContinuedException(previewId, it.id!!)
    }
    checkNoImportInProgress(preview.prisonId)
    if (cellCertificateUploadRepository.existsByPrisonIdAndModeAndStatusAndEndTimeAfter(
        prisonId = preview.prisonId,
        mode = CellCertificateUploadMode.IMPORT,
        status = CellCertificateUploadStatus.FINISHED,
        endTime = preview.requestedDate,
      )
    ) {
      throw CellCertificatePreviewOutOfDateException(previewId, preview.prisonId)
    }

    val import = cellCertificateUploadRepository.saveAndFlush(
      preview.copyAsImport(
        requestedBy = authenticationHolder.username ?: SYSTEM_USERNAME,
        requestedDate = LocalDateTime.now(clock),
      ),
    )
    sendMessageAfterCommit(import.id!!, CellCertificateUploadEventType.START_PROCESSING)

    log.info("Continued cell certificate preview $previewId as import ${import.id} for prison ${import.prisonId}")
    return import.toDto()
  }

  private fun storeUpload(prisonId: String, request: UpdateCapacityRequest, mode: CellCertificateUploadMode): CellCertificateUpload {
    activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    // Checked for a preview too, so that continuing it cannot then fail for a reason the preview could have shown.
    if (activePrisonService.isCertificationApprovalRequired(prisonId) && request.reasonForChange.isNullOrEmpty()) {
      throw ApprovalRequestRequiresReasonForChangeException(prisonId)
    }

    if (mode == CellCertificateUploadMode.IMPORT) {
      checkNoImportInProgress(prisonId)
    }

    val now = LocalDateTime.now(clock)
    val upload = CellCertificateUpload(
      prisonId = prisonId,
      status = CellCertificateUploadStatus.PENDING,
      mode = mode,
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
    sendMessageAfterCommit(
      saved.id!!,
      if (mode == CellCertificateUploadMode.PREVIEW) CellCertificateUploadEventType.START_PREVIEW else CellCertificateUploadEventType.START_PROCESSING,
    )

    log.info("Stored cell certificate ${mode.name.lowercase()} ${saved.id} for prison $prisonId with ${saved.totalRecords} records")
    return saved
  }

  private fun checkNoImportInProgress(prisonId: String) {
    cellCertificateUploadRepository.findFirstByPrisonIdAndModeAndStatusIn(prisonId, CellCertificateUploadMode.IMPORT, ACTIVE_STATUSES)?.let {
      throw CellCertificateUploadAlreadyInProgressException(prisonId)
    }
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
    return uploads.map { it.toDto(continuedAsUploadId = continuedAsUploadId(it)) }
  }

  /**
   * Returns a single upload with its per-cell results for drill-down.
   */
  @Transactional(readOnly = true)
  fun getCellCertificateUpload(uploadId: UUID): CellCertificateUploadDto = cellCertificateUploadRepository.findById(uploadId)
    .orElseThrow { CellCertificateUploadNotFoundException(uploadId) }
    .let { it.toDto(includeLocations = true, continuedAsUploadId = continuedAsUploadId(it)) }

  /**
   * Returns the upload behind an approval request, with its per-cell results, so the cell certificate import
   * request details page can show what the ingestion did.
   */
  @Transactional(readOnly = true)
  fun getCellCertificateUploadByApprovalRequest(approvalRequestId: UUID): CellCertificateUploadDto = cellCertificateUploadRepository.findByCertificationApprovalRequestId(approvalRequestId)
    ?.toDto(includeLocations = true)
    ?: throw CellCertificateUploadForApprovalRequestNotFoundException(approvalRequestId)

  private fun continuedAsUploadId(upload: CellCertificateUpload): UUID? = if (upload.isPreview()) cellCertificateUploadRepository.findFirstByPreviewUploadId(upload.id!!)?.id else null

  private fun sendMessageAfterCommit(uploadId: UUID, eventType: CellCertificateUploadEventType) {
    TransactionSynchronizationManager.registerSynchronization(
      object : TransactionSynchronization {
        override fun afterCommit() {
          sendMessage(uploadId, eventType)
        }
      },
    )
  }

  private fun sendMessage(uploadId: UUID, eventType: CellCertificateUploadEventType) {
    val event = CellCertificateUploadEvent(eventType = eventType, uploadId = uploadId)
    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(objectMapper.writeValueAsString(event))
        .build(),
    ).get()
    log.info("Sent $eventType message for cell certificate upload $uploadId")
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val ACTIVE_STATUSES = listOf(CellCertificateUploadStatus.PENDING, CellCertificateUploadStatus.STARTED)
  }
}
