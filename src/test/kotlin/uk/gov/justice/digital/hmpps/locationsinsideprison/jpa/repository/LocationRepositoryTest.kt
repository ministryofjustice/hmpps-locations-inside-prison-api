package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class LocationRepositoryTest : TestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  fun findCellsOnAWingTest() {
    val wing = buildLocation("A", "Wing A", locationType = LocationType.WING)
    val landing1 = buildLocation("A-1", "Landing 1 on Wing A", locationType = LocationType.LANDING)
    val landing2 = buildLocation("A-2", "Landing 2 on Wing A", locationType = LocationType.LANDING)
    val cell001L1 = buildLocation("A-1-001", "Cell 001 (Landing 1)", locationType = LocationType.CELL)
    val cell002L1 = buildLocation("A-1-002", "Cell 002 (Landing 1)", locationType = LocationType.CELL)
    val cell002L2 = buildLocation("A-2-001", "Cell 001 (Landing 2)", locationType = LocationType.CELL)

    wing.addChildLocation(landing1)
    wing.addChildLocation(landing2)
    landing1.addChildLocation(cell001L1)
    landing1.addChildLocation(cell002L1)
    landing2.addChildLocation(cell002L2)
    repository.save(wing)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val location = repository.findOneByPrisonIdAndCode("MDI", "A") ?: throw Exception("Location not found")

    assertThat(location.findAllLeafLocations()).containsExactlyInAnyOrder(cell001L1, cell002L1, cell002L2)
    assertThat(location.childLocations).containsExactlyInAnyOrder(landing1, landing2)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell2 = repository.findOneByPrisonIdAndCode(cell002L2.prisonId, cell002L2.code) ?: throw Exception("Location not found")
    assertThat(cell2.findTopLevelLocation()).isEqualTo(wing)
  }

  private fun buildLocation(
    code: String,
    description: String,
    prisonId: String = "MDI",
    locationType: LocationType = LocationType.CELL,
    active: Boolean = true,
    parent: Location? = null,
  ): Location {
    val now = LocalDateTime.now(clock)
    return Location(
      code = code,
      description = description,
      prisonId = prisonId,
      locationType = locationType,
      active = active,
      updatedBy = SYSTEM_USERNAME,
      whenUpdated = now,
      whenCreated = now,
      parent = parent,
    )
  }
}
