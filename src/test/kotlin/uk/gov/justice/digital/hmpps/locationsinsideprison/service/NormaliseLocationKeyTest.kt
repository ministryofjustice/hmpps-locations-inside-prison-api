package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CellCertificateUploadProcessingService.Companion.normaliseLocationKey

class NormaliseLocationKeyTest {
  @Test
  fun `names that differ only by dropped leading zeros compare equal`() {
    assertThat(normaliseLocationKey("LEI-B-1-5")).isEqualTo(normaliseLocationKey("LEI-B-1-005"))
    assertThat(normaliseLocationKey("LEI-B-01-005")).isEqualTo("LEI-B-1-5")
  }

  @Test
  fun `a part made only of zeros keeps one`() {
    assertThat(normaliseLocationKey("LEI-A-0-000")).isEqualTo("LEI-A-0-0")
  }

  @Test
  fun `parts with letters are left as they are`() {
    assertThat(normaliseLocationKey("LEI-Z-1-01S")).isEqualTo("LEI-Z-1-01S")
    assertThat(normaliseLocationKey("LEI-HB1-1-001")).isEqualTo("LEI-HB1-1-1")
  }

  @Test
  fun `different cells stay different`() {
    assertThat(normaliseLocationKey("LEI-B-1-5")).isNotEqualTo(normaliseLocationKey("LEI-B-1-050"))
  }
}
