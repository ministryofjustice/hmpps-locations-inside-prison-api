package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDashboardDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.CertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CertificationApprovalRequestRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CellCertificateNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.CurrentCellCertificateNotFoundException
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class CellCertificateService(
  private val cellCertificateRepository: CellCertificateRepository,
  private val residentialLocationRepository: ResidentialLocationRepository,
  private val certificationApprovalRequestRepository: CertificationApprovalRequestRepository,
  private val prisonService: PrisonService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createCellCertificate(
    approvedBy: String,
    approvedDate: LocalDateTime,
    approvalRequest: CertificationApprovalRequest,
    signedOperationCapacity: Int,
  ): CellCertificateDto {
    val currentCellCertificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(approvalRequest.prisonId)

    // Create the cell certificate
    val cellCertificate = cellCertificateRepository.saveAndFlush(
      CellCertificate(
        prisonId = approvalRequest.prisonId,
        approvedBy = approvedBy,
        approvedDate = approvedDate,
        certificationApprovalRequest = approvalRequest,
        signedOperationCapacity = signedOperationCapacity,
        locations = residentialLocationRepository.findAllByPrisonIdAndParentIsNull(approvalRequest.prisonId)
          .filter { !it.isPermanentlyDeactivated() && !it.isDraft() && it.isStructural() }
          .map {
            it.toCellCertificateLocation(approvalRequest, currentCellCertificate)
          }.toSortedSet(),
      ).apply {
        totalWorkingCapacity = locations.sumOf { it.workingCapacity ?: 0 }
        totalMaxCapacity = locations.sumOf { it.maxCapacity ?: 0 }
        totalCertifiedNormalAccommodation = locations.sumOf { it.certifiedNormalAccommodation ?: 0 }
      },
    )
    // Mark any existing current certificates as not current
    currentCellCertificate?.markAsNotCurrent()

    log.info("Created cell certificate for prison ${approvalRequest.prisonId} with ID ${cellCertificate.id}")
    return cellCertificate.toDto()
  }

  fun getCellCertificate(id: UUID): CellCertificateDto {
    val cellCertificate = cellCertificateRepository.findById(id).orElseThrow { CellCertificateNotFoundException(id) }
    return cellCertificate.toDto(showLocations = true)
  }

  fun getCellCertificatesForPrison(prisonId: String): List<CellCertificateDto> = cellCertificateRepository.findByPrisonIdOrderByApprovedDateDesc(prisonId)
    .map { it.toDto() }

  fun getCurrentCellCertificateForPrison(prisonId: String): CellCertificateDto {
    val cellCertificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(prisonId)
      ?: throw CurrentCellCertificateNotFoundException(prisonId)
    return cellCertificate.toDto(showLocations = true)
  }

  fun hasCurrentCellCertificate(prisonId: String): Boolean = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(prisonId) != null

  @Transactional(readOnly = true)
  fun getCellCertificateDashboard(): List<CellCertificateDashboardDto> {
    val summaries = cellCertificateRepository.findCurrentCertificateSummaries()
    if (summaries.isEmpty()) return emptyList()

    val pendingCounts = certificationApprovalRequestRepository
      .countByStatusGroupedByPrison(ApprovalRequestStatus.PENDING)
      .associate { it.prisonId to it.count.toInt() }

    val prisonNames = prisonService.getPrisonNames()

    return summaries
      .map { summary ->
        CellCertificateDashboardDto(
          prisonId = summary.prisonId,
          prisonName = prisonNames[summary.prisonId] ?: summary.prisonId,
          certifiedWorkingCapacity = summary.totalWorkingCapacity,
          signedOperationCapacity = summary.signedOperationCapacity,
          pendingChangeRequests = pendingCounts[summary.prisonId] ?: 0,
          certificateLastUpdated = summary.approvedDate,
        )
      }
      .sortedBy { it.prisonName.lowercase() }
  }

  fun updateSpecialistCellTypesInCurrentCertificate(cell: Cell) {
    val currentCert = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(cell.prisonId) ?: return
    currentCert.findLocationInCertificate(cell.getPathHierarchy())?.let { certLocation ->
      certLocation.specialistCellTypes = cell.getSpecialistCellTypesAsCSV()
    }
  }

  fun updateUsedForTypesInCurrentCertificate(cell: Cell) {
    val currentCert = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(cell.prisonId) ?: return
    currentCert.findLocationInCertificate(cell.getPathHierarchy())?.let { certLocation ->
      certLocation.usedForTypes = cell.getUsedForValuesAsCSV()
    }
  }
}
