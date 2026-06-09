package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocation
import java.util.UUID

@Repository
interface CellCertificateUploadLocationRepository : JpaRepository<CellCertificateUploadLocation, UUID>
