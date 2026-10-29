package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import java.time.LocalDateTime
import java.util.Optional

class ActivePrisonServiceTest {
  private val prisonConfigurationRepository: PrisonConfigurationRepository = mock()
  private val service = ActivePrisonService(prisonConfigurationRepository, resiServiceActivatedPerPrison = true)

  private val resiInactivePrison = PrisonConfiguration(
    id = "LEI",
    resiLocationServiceActive = false,
    nonResiServiceActive = false,
    whenUpdated = LocalDateTime.now(clock),
    updatedBy = "TEST",
  )

  @Test
  fun `prison with a configuration is active even when residential service is inactive`() {
    whenever(prisonConfigurationRepository.findById("LEI")).thenReturn(Optional.of(resiInactivePrison))

    assertThat(service.isActivePrison("LEI")).isTrue()
  }

  @Test
  fun `prison without a configuration is not active`() {
    whenever(prisonConfigurationRepository.findById("XYZ")).thenReturn(Optional.empty())

    assertThat(service.isActivePrison("XYZ")).isFalse()
  }

  @Test
  fun `all prisons are active when not activated per prison`() {
    val allActiveService = ActivePrisonService(prisonConfigurationRepository, resiServiceActivatedPerPrison = false)

    assertThat(allActiveService.isActivePrison("XYZ")).isTrue()
  }
}
