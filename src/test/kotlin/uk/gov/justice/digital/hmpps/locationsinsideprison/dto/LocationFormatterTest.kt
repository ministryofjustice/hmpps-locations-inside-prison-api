package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LocationFormatterTest {

  @ParameterizedTest(name = "[{0}] becomes [{1}]")
  @CsvSource(
    "' Gym', 'Gym'",
    "'Gym ', 'Gym'",
    "'  Main   Gym  ', 'Main Gym'",
    "'Already tidy', 'Already tidy'",
    "'Reception-Prop Box', 'Reception-Prop Box'",
  )
  fun `removes spaces from the ends and collapses repeated spaces`(name: String, expected: String) {
    assertThat(name.tidyLocalName()).isEqualTo(expected)
  }

  @Test
  fun `treats tabs, new lines and non-breaking spaces as spaces`() {
    assertThat("\tArt  Room\n".tidyLocalName()).isEqualTo("Art Room")
  }

  @Test
  fun `a name with nothing but spaces becomes no name`() {
    assertThat("   ".tidyLocalName()).isNull()
    assertThat(" \t".tidyLocalName()).isNull()
    assertThat("".tidyLocalName()).isNull()
    assertThat((null as String?).tidyLocalName()).isNull()
  }
}
