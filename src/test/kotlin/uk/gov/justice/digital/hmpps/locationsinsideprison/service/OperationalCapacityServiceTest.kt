package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.OperationalCapacityRepository
import java.time.LocalDateTime

class OperationalCapacityServiceTest {
  private val operationalCapacityRepository: OperationalCapacityRepository = mock()
  private val service: OperationalCapacityService = OperationalCapacityService(operationalCapacityRepository)

  @Test
  fun `Get operational capacity for the prison when record found`() {
    whenever(operationalCapacityRepository.findOneByPrisonId(any())).thenReturn(
      OperationalCapacity(1, 130, "MDI", LocalDateTime.now(), "Approved by"),
    )
    val result = service.getOperationalCapacity("MDI")
    assertThat(result?.capacity).isEqualTo(130)
  }

  @Test
  fun `Get null for the prison when record not found`() {
    whenever(operationalCapacityRepository.findOneByPrisonId(any())).thenReturn(null)
    val result = service.getOperationalCapacity("MDI")
    assertThat(result).isNull()
  }
}
