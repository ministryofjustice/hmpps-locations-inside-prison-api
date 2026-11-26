package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import java.time.LocalDateTime

/**
 * Runs V1_115 (MAPA-476) against seeded data. Flyway has already applied it to an empty database by the time the
 * test starts, so the script is run again here, which is safe because it only acts on rooms still in the V1_74 state.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class RemoveCapacityFromConvertedRoomsMigrationTest : TestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @Autowired
  lateinit var linkedTransactionRepository: LinkedTransactionRepository

  @Autowired
  lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  lateinit var entityManager: EntityManager

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  fun `removes the capacity left on converted rooms and records it in their history`() {
    val transaction = linkedTransactionRepository.saveAndFlush(
      LinkedTransaction(
        prisonId = "MDI",
        transactionInvokedBy = EXPECTED_USERNAME,
        transactionType = TransactionType.LOCATION_CREATE,
        transactionDetail = "TEST",
        txStartTime = LocalDateTime.now(clock),
      ),
    )
    val wing = buildResidentialLocation(pathHierarchy = "B", locationType = LocationType.WING)
    val landing = buildResidentialLocation(pathHierarchy = "B-2", locationType = LocationType.LANDING)
    wing.addChildLocation(landing)
    landing.addChildLocation(buildCell(pathHierarchy = "B-2-002", linkedTransaction = transaction))
    landing.addChildLocation(convertedRoom("B-2-001", Capacity(maxCapacity = 0, workingCapacity = 0, certifiedNormalAccommodation = 1), transaction))
    landing.addChildLocation(convertedRoom("B-2-003", Capacity(maxCapacity = 15, workingCapacity = 15, certifiedNormalAccommodation = 15), transaction))
    repository.saveAndFlush(wing)

    jdbcTemplate.execute(ClassPathResource("db/migration/V1_115__remove_capacity_from_converted_rooms.sql").getContentAsString(Charsets.UTF_8))
    entityManager.clear()

    val room = repository.findOneByPrisonIdAndPathHierarchy("MDI", "B-2-001") as Cell
    assertThat(room.capacity).isNull()
    assertThat(room.getHistoryAsList().filter { it.attributeName.name == "CERTIFIED_CAPACITY" })
      .singleElement()
      .satisfies({
        assertThat(it.oldValue).isEqualTo("1")
        assertThat(it.newValue).isEqualTo("None")
        assertThat(it.linkedTransaction?.transactionType).isEqualTo(TransactionType.CAPACITY_CHANGE)
      })

    // A converted room holding real max and working capacity is not the V1_74 pattern and is left alone
    val roomWithCapacity = repository.findOneByPrisonIdAndPathHierarchy("MDI", "B-2-003") as Cell
    assertThat(roomWithCapacity.capacity?.certifiedNormalAccommodation).isEqualTo(15)

    val cell = repository.findOneByPrisonIdAndPathHierarchy("MDI", "B-2-002") as Cell
    assertThat(cell.capacity?.certifiedNormalAccommodation).isEqualTo(1)
  }

  private fun convertedRoom(pathHierarchy: String, capacity: Capacity, transaction: LinkedTransaction) = buildCell(
    pathHierarchy = pathHierarchy,
    capacity = capacity,
    certifiedCell = false,
    linkedTransaction = transaction,
  ).apply { convertedCellType = ConvertedCellType.STORE }
}
