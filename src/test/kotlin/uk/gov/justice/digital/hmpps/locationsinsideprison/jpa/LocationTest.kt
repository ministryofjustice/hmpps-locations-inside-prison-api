package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class LocationTest {

  val clock: Clock = Clock.fixed(
    Instant.parse("2023-12-05T12:34:56+00:00"),
    ZoneId.of("Europe/London"),
  )

  @Test
  fun `location history filters out duplicates`() {
    val location = generateCellLocation()

    val now = LocalDateTime.now(clock)
    val history1 = location.addHistory(LocationAttribute.ATTRIBUTES, null, "new", "user", now)
    val history2 = location.addHistory(LocationAttribute.ATTRIBUTES, null, "new", "user", now)

    assertThat(history1).isEqualTo(history2)
    assertThat(location.getHistory()).hasSize(1)
  }
}

fun generateCellLocation() = Cell(
  code = "001",
  prisonId = "MDI",
  locationType = LocationType.CELL,
  pathHierarchy = "MDI-001",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)
