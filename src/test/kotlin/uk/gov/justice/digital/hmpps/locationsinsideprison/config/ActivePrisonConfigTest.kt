package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ActivePrisonConfigTest {
  @Test
  fun `isActivePrison should return true when *** is in active prisons list`() {
    val activePrisonConfig = ActivePrisonConfig(listOf("***"))
    Assertions.assertThat(activePrisonConfig.isActivePrison("MDI")).isEqualTo(true)
  }

  @Test
  fun `isActivePrison should return true when prison is in active prisons list`() {
    val activePrisonConfig = ActivePrisonConfig(listOf("MDI", "ABC"))
    Assertions.assertThat(activePrisonConfig.isActivePrison("MDI")).isEqualTo(true)
  }

  @Test
  fun `isActivePrison should return false when prison is not in the active prisons list`() {
    val activePrisonConfig = ActivePrisonConfig(listOf("MDI", "ABC"))
    Assertions.assertThat(activePrisonConfig.isActivePrison("XYZ")).isEqualTo(false)
  }
}
