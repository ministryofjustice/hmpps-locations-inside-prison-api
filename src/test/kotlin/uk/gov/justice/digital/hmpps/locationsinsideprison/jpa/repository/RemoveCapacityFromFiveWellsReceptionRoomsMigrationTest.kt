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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import java.time.LocalDateTime

/**
 * Runs V1_116 (MAPA-476) against seeded data. Flyway has already applied it to an empty database by the time the
 * test starts, so the script is run again here, which is safe because it only acts on locations that still hold capacity.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class RemoveCapacityFromFiveWellsReceptionRoomsMigrationTest : TestBase() {

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
  fun `removes the capacity from the Five Wells reception locations and records it in their history`() {
    val fwiTransaction = transaction("FWI")
    val fwiReception = reception("FWI")
    fwiReception.addChildLocation(buildVirtualResidentialLocation(prisonId = "FWI", pathHierarchy = "1", capacity = fifteen()))
    fwiReception.addChildLocation(buildCell(prisonId = "FWI", pathHierarchy = "2", capacity = fifteen(), linkedTransaction = fwiTransaction))
    fwiReception.addChildLocation(buildVirtualResidentialLocation(prisonId = "FWI", pathHierarchy = "6", capacity = fifteen()))
    repository.saveAndFlush(fwiReception.getParent()!!)

    val mdiReception = reception("MDI")
    mdiReception.addChildLocation(buildVirtualResidentialLocation(prisonId = "MDI", pathHierarchy = "1", capacity = fifteen()))
    repository.saveAndFlush(mdiReception.getParent()!!)

    jdbcTemplate.execute(ClassPathResource("db/migration/V1_116__remove_capacity_from_five_wells_reception_rooms.sql").getContentAsString(Charsets.UTF_8))
    entityManager.clear()

    val reception = repository.findOneByPrisonIdAndPathHierarchy("FWI", "RECP-1-1") as VirtualResidentialLocation
    assertThat(reception.capacity).isNull()
    assertThat(reception.getMaxCapacity()).isZero()
    val capacityHistory = reception.getHistoryAsList()
      .filter { it.attributeName.name in listOf("MAX_CAPACITY", "WORKING_CAPACITY", "CERTIFIED_CAPACITY") }
    assertThat(capacityHistory.map { it.attributeName.name })
      .containsExactlyInAnyOrder("MAX_CAPACITY", "WORKING_CAPACITY", "CERTIFIED_CAPACITY")
    assertThat(capacityHistory).allSatisfy({
      assertThat(it.oldValue).isEqualTo("15")
      assertThat(it.newValue).isEqualTo("None")
      assertThat(it.linkedTransaction?.transactionType).isEqualTo(TransactionType.CAPACITY_CHANGE)
    })

    // A cell at one of the named locations is not a reception location and keeps its capacity
    assertThat((repository.findOneByPrisonIdAndPathHierarchy("FWI", "RECP-1-2") as Cell).capacity?.maxCapacity).isEqualTo(15)

    // Reception locations outside the five named, at Five Wells or elsewhere, are left alone
    assertThat((repository.findOneByPrisonIdAndPathHierarchy("FWI", "RECP-1-6") as VirtualResidentialLocation).capacity?.maxCapacity).isEqualTo(15)
    assertThat((repository.findOneByPrisonIdAndPathHierarchy("MDI", "RECP-1-1") as VirtualResidentialLocation).capacity?.maxCapacity).isEqualTo(15)
  }

  // Returns RECP-1, with RECP above it, so the locations added to it get their full path
  private fun reception(prisonId: String) = buildVirtualResidentialLocation(prisonId = prisonId, pathHierarchy = "1")
    .also { buildVirtualResidentialLocation(prisonId = prisonId, pathHierarchy = "RECP").addChildLocation(it) }

  private fun transaction(prisonId: String) = linkedTransactionRepository.saveAndFlush(
    LinkedTransaction(
      prisonId = prisonId,
      transactionInvokedBy = EXPECTED_USERNAME,
      transactionType = TransactionType.LOCATION_CREATE,
      transactionDetail = "TEST",
      txStartTime = LocalDateTime.now(clock),
    ),
  )

  private fun fifteen() = Capacity(maxCapacity = 15, workingCapacity = 15, certifiedNormalAccommodation = 15)
}
