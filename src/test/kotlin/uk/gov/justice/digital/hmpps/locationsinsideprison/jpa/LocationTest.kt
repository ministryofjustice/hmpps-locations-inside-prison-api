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
    assertThat(location.getHistoryAsList()).hasSize(1)
  }

  @Test
  fun `toLocationGroupDto sets name to code`() {
    val location = generateWingLocation(null)
    val dto = location.toLocationGroupDto()
    assertThat(dto.name).isEqualTo("A")
  }

  @Test
  fun `toLocationGroupDto sets name to local name`() {
    val location = generateWingLocation("Wing A")
    val dto = location.toLocationGroupDto()
    assertThat(dto.name).isEqualTo("Wing A")
  }
}

fun generateWingLocation(localName: String?) = ResidentialLocation(
  code = "A",
  prisonId = "MDI",
  locationType = LocationType.WING,
  localName = localName,
  pathHierarchy = "MDI-A",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)

fun generateCellLocation() = Cell(
  code = "001",
  prisonId = "MDI",
  locationType = LocationType.CELL,
  pathHierarchy = "MDI-001",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)
