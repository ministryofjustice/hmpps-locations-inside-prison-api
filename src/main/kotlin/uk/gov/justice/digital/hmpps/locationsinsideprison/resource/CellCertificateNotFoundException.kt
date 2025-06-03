package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import java.util.UUID

@ResponseStatus(HttpStatus.NOT_FOUND)
class CellCertificateNotFoundException(id: UUID) : RuntimeException("Cell certificate with id $id not found")
