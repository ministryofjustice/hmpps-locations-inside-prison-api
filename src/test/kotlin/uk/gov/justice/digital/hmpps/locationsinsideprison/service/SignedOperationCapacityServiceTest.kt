package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import java.time.LocalDateTime

class SignedOperationCapacityServiceTest {
  private val prisonSignedOperationalCapacityRepository: PrisonSignedOperationCapacityRepository = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val service: SignedOperationCapacityService = SignedOperationCapacityService(
    prisonSignedOperationalCapacityRepository,
    telemetryClient,
  )

  @Test
  fun `Get operational capacity for the prison when record found`() {
    whenever(prisonSignedOperationalCapacityRepository.findOneByPrisonId(any())).thenReturn(
      PrisonSignedOperationCapacity(1, 130, "MDI", LocalDateTime.now(), "Approved by"),
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
}
