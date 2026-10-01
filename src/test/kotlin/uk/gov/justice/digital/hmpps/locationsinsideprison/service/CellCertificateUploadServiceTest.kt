package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUpload
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateUploadRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCapacityUpdateDetail
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateUploadAlreadyInProgressException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.UpdateCapacityRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.util.UUID

/**
 * Two imports started for a prison at the same moment can both pass the "already in progress" check, which runs
 * before the save. The database index then rejects the second; that must be reported as "already in progress"
 * rather than a server error. The race cannot be forced reliably through the API, so the rejection is simulated.
 */
class CellCertificateUploadServiceTest {
  private val cellCertificateUploadRepository: CellCertificateUploadRepository = mock()
  private val activePrisonService: ActivePrisonService = mock()
  private val service = CellCertificateUploadService(
    cellCertificateUploadRepository,
    activePrisonService,
    mock<HmppsQueueService>(),
    mock<HmppsAuthenticationHolder>(),
    ObjectMapper(),
    clock,
  )

  private val request = UpdateCapacityRequest(
    locations = mapOf("MDI-A-1-001" to CellCapacityUpdateDetail(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2)),
  )

  private fun indexViolation(index: String) = DataIntegrityViolationException(
    "could not execute statement",
    RuntimeException("ERROR: duplicate key value violates unique constraint \"$index\""),
  )

  @BeforeEach
  fun setUp() {
    whenever(activePrisonService.getPrisonConfiguration("MDI")).thenReturn(
      PrisonConfiguration(id = "MDI", whenUpdated = LocalDateTime.now(clock), updatedBy = "TEST"),
    )
  }

  @Test
  fun `an import that loses the race is reported as already in progress`() {
    whenever(cellCertificateUploadRepository.saveAndFlush(any<CellCertificateUpload>()))
      .thenThrow(indexViolation("cell_certificate_upload_active_prison_idx"))

    assertThatThrownBy { service.requestCellCertificateUpload("MDI", request) }
      .isInstanceOf(CellCertificateUploadAlreadyInProgressException::class.java)
  }

  @Test
  fun `continuing a preview that loses the race is reported as already in progress`() {
    val previewId = UUID.randomUUID()
    whenever(cellCertificateUploadRepository.findByIdForUpdate(previewId)).thenReturn(
      CellCertificateUpload(
        id = previewId,
        prisonId = "MDI",
        mode = CellCertificateUploadMode.PREVIEW,
        status = CellCertificateUploadStatus.FINISHED,
        requestedBy = "TEST",
        requestedDate = LocalDateTime.now(clock),
      ),
    )
    whenever(cellCertificateUploadRepository.saveAndFlush(any<CellCertificateUpload>()))
      .thenThrow(indexViolation("cell_certificate_upload_active_prison_idx"))

    assertThatThrownBy { service.continuePreview(previewId) }
      .isInstanceOf(CellCertificateUploadAlreadyInProgressException::class.java)
  }

  @Test
  fun `any other integrity failure is not disguised`() {
    val otherFailure = indexViolation("some_other_constraint")
    whenever(cellCertificateUploadRepository.saveAndFlush(any<CellCertificateUpload>())).thenThrow(otherFailure)

    assertThatThrownBy { service.requestCellCertificateUpload("MDI", request) }.isSameAs(otherFailure)
  }
}
