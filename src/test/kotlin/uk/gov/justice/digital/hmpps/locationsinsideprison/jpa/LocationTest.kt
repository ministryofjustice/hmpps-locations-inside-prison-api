package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class LocationTest {

  val clock: Clock = Clock.fixed(
    Instant.parse("2023-12-05T12:34:56+00:00"),
    ZoneId.of("Europe/London"),
  )

  @Test
  fun `location history filters out duplicates`() {
    val location = generateCellLocation()

    val linkedTransaction = LinkedTransaction(
      transactionId = UUID.randomUUID(),
      prisonId = "MDI",
      transactionInvokedBy = EXPECTED_USERNAME,
      transactionType = TransactionType.LOCATION_CREATE,
      transactionDetail = "TEST",
      txStartTime = LocalDateTime.now(TestBase.clock),
      txEndTime = LocalDateTime.now(TestBase.clock),
    )

    val now = LocalDateTime.now(clock)
    val history1 = location.addHistory(LocationAttribute.ATTRIBUTES, null, "new", "user", now, linkedTransaction)
    val history2 = location.addHistory(LocationAttribute.ATTRIBUTES, null, "new", "user", now, linkedTransaction)

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

  @Test
  fun `toLocationGroupDto sets name to code and children list`() {
    val locationGroupDto = generateLocationGroupDto(mutableListOf(childLocation1, childLocation2))

    assertThat(locationGroupDto.name).isEqualTo("Block B")
    assertThat(locationGroupDto.key).isEqualTo("B")
    assertThat(locationGroupDto.children).isNotNull
    assertThat(locationGroupDto.children?.size).isEqualTo(2)

    val child1 = locationGroupDto.children?.get(0)
    val child2 = locationGroupDto.children?.get(1)

    assertThat(child1?.name).isEqualTo("Landing B-1")
    assertThat(child1?.key).isEqualTo("1")

    assertThat(child2?.name).isEqualTo("Landing B-2")
    assertThat(child2?.key).isEqualTo("2")
  }

  @Test
  fun `toPrisonHierarchyDto handles includeInactive flag when set to false`() {
    val wing = generateWingLocation("Wing A")

    val activeLanding = generateLandingLocation("Landing 1")
    wing.addChildLocation(activeLanding)
    val activeCell = generateCellLocation()
    activeLanding.addChildLocation(activeCell)
    val inactiveCell = generateCellLocation().also { it.status = LocationStatus.INACTIVE }
    activeLanding.addChildLocation(inactiveCell)

    val inactiveLanding = generateLandingLocation("Landing 2").also { it.status = LocationStatus.INACTIVE }
    wing.addChildLocation(inactiveLanding)

    val prisonHierarchyDto = wing.toPrisonHierarchyDto(includeInactive = false)
    assertThat(prisonHierarchyDto.subLocations.orEmpty().size).isEqualTo(1)
    assertThat(prisonHierarchyDto.subLocations?.get(0)?.subLocations.orEmpty().size).isEqualTo(1)
  }

  @Test
  fun `toPrisonHierarchyDto handles includeInactive flag when set to true`() {
    val wing = generateWingLocation("Wing A")

    val activeLanding = generateLandingLocation("Landing 1")
    wing.addChildLocation(activeLanding)
    val activeCell = generateCellLocation()
    activeLanding.addChildLocation(activeCell)
    val inactiveCell = generateCellLocation().also { it.status = LocationStatus.INACTIVE }
    activeLanding.addChildLocation(inactiveCell)

    val inactiveLanding = generateLandingLocation("Landing 2").also { it.status = LocationStatus.INACTIVE }
    wing.addChildLocation(inactiveLanding)

    val prisonHierarchyDto = wing.toPrisonHierarchyDto(includeInactive = true)
    assertThat(prisonHierarchyDto.subLocations.orEmpty().size).isEqualTo(2)
    assertThat(prisonHierarchyDto.subLocations?.get(0)?.subLocations.orEmpty().size).isEqualTo(2)
  }
}

fun generateWingLocation(localName: String?) = ResidentialLocation(
  id = UUID.randomUUID(),
  code = "A",
  prisonId = "MDI",
  locationType = LocationType.WING,
  status = LocationStatus.ACTIVE,
  localName = localName,
  pathHierarchy = "MDI-A",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)

fun generateLandingLocation(localName: String?) = ResidentialLocation(
  id = UUID.randomUUID(),
  code = "1",
  prisonId = "MDI",
  locationType = LocationType.LANDING,
  status = LocationStatus.ACTIVE,
  localName = localName,
  pathHierarchy = "MDI-A-1",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)

fun generateCellLocation() = Cell(
  id = UUID.randomUUID(),
  code = "001",
  prisonId = "MDI",
  locationType = LocationType.CELL,
  cellMark = "1",
  status = LocationStatus.ACTIVE,
  pathHierarchy = "MDI-001",
  createdBy = "user",
  whenCreated = LocalDateTime.now(),
  childLocations = mutableListOf(),
)

fun generateLocationGroupDto(childLocationList: MutableList<LocationGroupDto>) = LocationGroupDto(
  name = "Block B",
  key = "B",
  children = List(childLocationList.size) { childLocationList[it] },
)

val childLocation1 = LocationGroupDto(
  name = "Landing B-1",
  key = "1",
  children = mutableListOf(),
)

val childLocation2 = LocationGroupDto(
  name = "Landing B-2",
  key = "2",
  children = mutableListOf(),
)
