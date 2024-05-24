package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityValidRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import java.time.LocalDateTime

class SignedOperationCapacityServiceTest {
  private val prisonSignedOperationalCapacityRepository: PrisonSignedOperationCapacityRepository = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val service: SignedOperationCapacityService = SignedOperationCapacityService(
    prisonSignedOperationalCapacityRepository,
    telemetryClient,
    clock,
  )

  @Test
  fun `Get signed operation capacity for the prison when record found`() {
    whenever(prisonSignedOperationalCapacityRepository.findOneByPrisonId(any())).thenReturn(
      PrisonSignedOperationCapacity(1, 130, "MDI", LocalDateTime.now(clock), "Updated by"),
    )
    val result = service.getSignedOperationalCapacity("MDI")
    assertThat(result?.signedOperationCapacity).isEqualTo(130)
  }

  @Test
  fun `Get null for the prison when record not found`() {
    whenever(prisonSignedOperationalCapacityRepository.findOneByPrisonId(any())).thenReturn(null)
    val result = service.getSignedOperationalCapacity("MDI")
    assertThat(result).isNull()
  }

  @Test
  fun `Create signed operation capacity`() {
    val signedOperationCapacity = 130
    val prisonId = "MDI"
    val updatedBy = "USER"
    val request: SignedOperationCapacityValidRequest = mock()
    val prisonSignedOperationCapacity: PrisonSignedOperationCapacity = mock()

    whenever(request.signedOperationCapacity).thenReturn(signedOperationCapacity)
    whenever(request.prisonId).thenReturn(prisonId)
    whenever(request.updatedBy).thenReturn(updatedBy)
    whenever(prisonSignedOperationalCapacityRepository.findOneByPrisonId(any())).thenReturn(null)
    whenever(prisonSignedOperationalCapacityRepository.save(any())).thenReturn(prisonSignedOperationCapacity)

    service.saveSignedOperationalCapacity(request)

    verify(prisonSignedOperationalCapacityRepository).save(
      PrisonSignedOperationCapacity(
        id = null,
        signedOperationCapacity = signedOperationCapacity,
        prisonId = prisonId,
        updatedBy = updatedBy,
        whenUpdated = LocalDateTime.now(clock),
      ),
    )
    verify(telemetryClient).trackEvent(any(), anyMap(), anyOrNull())
  }

  @Test
  fun `Update signed operation capacity`() {
    val signedOperationCapacity = 130
    val prisonId = "MDI"
    val updatedBy = "USER"
    val id = 1L
    val request: SignedOperationCapacityValidRequest = mock()
    val prisonSignedOperationCapacity: PrisonSignedOperationCapacity = mock()

    val existingRecord = PrisonSignedOperationCapacity(
      id = id,
      signedOperationCapacity = signedOperationCapacity,
      prisonId = prisonId,
      updatedBy = updatedBy,
      whenUpdated = LocalDateTime.now(clock),
    )

    whenever(request.signedOperationCapacity).thenReturn(signedOperationCapacity)
    whenever(request.prisonId).thenReturn(prisonId)
    whenever(request.updatedBy).thenReturn(updatedBy)
    whenever(prisonSignedOperationalCapacityRepository.findOneByPrisonId(any())).thenReturn(existingRecord)
    whenever(prisonSignedOperationalCapacityRepository.save(any())).thenReturn(prisonSignedOperationCapacity)

    service.saveSignedOperationalCapacity(request)

    verify(prisonSignedOperationalCapacityRepository).save(
      PrisonSignedOperationCapacity(
        id = id,
        signedOperationCapacity = signedOperationCapacity,
        prisonId = prisonId,
        updatedBy = updatedBy,
        whenUpdated = LocalDateTime.now(clock),
      ),
    )
    verify(telemetryClient).trackEvent(any(), anyMap(), anyOrNull())
  }
}
