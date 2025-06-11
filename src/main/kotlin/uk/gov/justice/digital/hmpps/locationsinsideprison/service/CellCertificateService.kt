package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CellCertificate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.CertificationApprovalRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellCertificateRepository
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
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createCellCertificate(location: ResidentialLocation, approvedBy: String, approvedDate: LocalDateTime, approvalRequest: CertificationApprovalRequest): CellCertificateDto {
    // Mark any existing current certificates as not current
    cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(location.prisonId)?.markAsNotCurrent()

    // Create the cell certificate
    val cellCertificate = cellCertificateRepository.save(
      CellCertificate(
        prisonId = location.prisonId,
        approvedBy = approvedBy,
        approvedDate = approvedDate,
        certificationApprovalRequest = approvalRequest,
      ).apply {
        locations = residentialLocationRepository.findAllByPrisonIdAndParentIsNull(location.prisonId)
          .filter { !it.isPermanentlyDeactivated() && !it.isDraft() && it.isStructural() }
          .map {
            it.toCellCertificateLocation(this, location)
          }.toSortedSet()

        totalWorkingCapacity = locations.sumOf { it.workingCapacity ?: 0 }
        totalMaxCapacity = locations.sumOf { it.maxCapacity ?: 0 }
        totalCertifiedNormalAccommodation = locations.sumOf { it.certifiedNormalAccommodation ?: 0 }
      },
    )

    log.info("Created cell certificate for prison ${location.prisonId} with ID ${cellCertificate.id}")
    return cellCertificate.toDto()
  }

  fun getCellCertificate(id: UUID): CellCertificateDto {
    val cellCertificate = cellCertificateRepository.findById(id)
      .orElseThrow { CellCertificateNotFoundException(id) }
    return cellCertificate.toDto(showLocations = true)
  }

  fun getCellCertificatesForPrison(prisonId: String): List<CellCertificateDto> = cellCertificateRepository.findByPrisonIdOrderByApprovedDateDesc(prisonId)
    .map { it.toDto() }

  fun getCurrentCellCertificateForPrison(prisonId: String): CellCertificateDto {
    val cellCertificate = cellCertificateRepository.findByPrisonIdAndCurrentIsTrue(prisonId)
      ?: throw CurrentCellCertificateNotFoundException(prisonId)
    return cellCertificate.toDto(showLocations = true)
  }
}
