package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.DeactivationApprovalRequest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class LocationTest {

  val clock: Clock = Clock.fixed(
    Instant.parse("2023-12-05T12:34:56.123456+00:00"),
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

  @Test
  fun `findPendingApprovalRequestsBelowThisLevel returns pending requests on descendants and ignores non-pending`() {
    val wing = generateWingLocation("Wing A")
    val landing = generateLandingLocation("Landing 1")
    wing.addChildLocation(landing)
    val pendingCell = generateCellLocation()
    landing.addChildLocation(pendingCell)
    val approvedCell = generateCellLocation()
    landing.addChildLocation(approvedCell)

    pendingCell.approvalRequests.add(deactivationApprovalRequest(pendingCell))
    approvedCell.approvalRequests.add(
      deactivationApprovalRequest(approvedCell).also { it.status = ApprovalRequestStatus.APPROVED },
    )

    val pending = wing.findPendingApprovalRequestsBelowThisLevel()
    assertThat(pending).hasSize(1)
    assertThat(pending.first().location).isEqualTo(pendingCell)
  }

  @Test
  fun `findPendingApprovalRequestsBelowThisLevel only looks below the location, not at the location itself`() {
    val wing = generateWingLocation("Wing A")
    val cell = generateCellLocation()
    wing.addChildLocation(cell)
    cell.approvalRequests.add(deactivationApprovalRequest(cell))

    // the cell has the pending request, so nothing is pending *below* it
    assertThat(cell.findPendingApprovalRequestsBelowThisLevel()).isEmpty()
    // but it is pending below the wing
    assertThat(wing.findPendingApprovalRequestsBelowThisLevel()).hasSize(1)
  }

  @Nested
  @DisplayName("Archiving a location")
  inner class ArchivingALocation {

    @Test
    fun `zeroes and de-certifies every cell below a wing`() {
      val wing = wingWithTwoLandings()

      wing.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      assertThat(wing.findAllLeafLocations().filterIsInstance<Cell>()).allSatisfy { cell ->
        assertThat(cell.getMaxCapacity()).isEqualTo(0)
        assertThat(cell.getCurrentlyHeldWorkingCapacity()).isEqualTo(0)
        assertThat(cell.getCertifiedNormalAccommodation()).isEqualTo(0)
        assertThat(cell.isCertified()).isFalse()
        assertThat(cell.temporarilyOffCellCert).isFalse()
      }
    }

    @Test
    fun `zeroes and de-certifies the cells on a landing`() {
      val wing = wingWithTwoLandings()
      val landings = wing.getResidentialLocationsBelowThisLevel()
      val landing = landings.first()
      val untouchedLanding = landings.last()

      landing.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      assertThat(landing.findAllLeafLocations().filterIsInstance<Cell>()).allSatisfy { cell ->
        assertThat(cell.getMaxCapacity()).isEqualTo(0)
        assertThat(cell.isCertified()).isFalse()
      }
      // the rest of the wing is untouched
      assertThat(untouchedLanding.findAllLeafLocations().filterIsInstance<Cell>()).allSatisfy { cell ->
        assertThat(cell.getMaxCapacity()).isEqualTo(2)
        assertThat(cell.isCertified()).isTrue()
      }
    }

    @Test
    fun `zeroes and de-certifies a single cell archived on its own`() {
      val cell = cell("001")

      cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      assertThat(cell.getMaxCapacity()).isEqualTo(0)
      assertThat(cell.getCurrentlyHeldWorkingCapacity()).isEqualTo(0)
      assertThat(cell.getCertifiedNormalAccommodation()).isEqualTo(0)
      assertThat(cell.isCertified()).isFalse()
      assertThat(cell.temporarilyOffCellCert).isFalse()
    }

    @Test
    fun `leaves converted cells alone`() {
      val landing = landing("1")
      val cell = cell("001")
      val convertedCell = cell("002").apply { convertedCellType = ConvertedCellType.OFFICE }
      landing.addChildLocation(cell)
      landing.addChildLocation(convertedCell)

      landing.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      assertThat(cell.getMaxCapacity()).isEqualTo(0)
      assertThat(convertedCell.getMaxCapacity()).isEqualTo(2)
      assertThat(convertedCell.isCertified()).isTrue()
    }

    @Test
    fun `does not strip a cell already archived in its own right`() {
      val landing = landing("1")
      val alreadyArchived = cell("001")
      val stillLive = cell("002")
      landing.addChildLocation(alreadyArchived)
      landing.addChildLocation(stillLive)
      alreadyArchived.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())
      val historyAfterOwnArchive = alreadyArchived.getHistoryAsList().size

      landing.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      assertThat(stillLive.getMaxCapacity()).isEqualTo(0)
      // no second set of rows recording a strip that did not happen
      assertThat(alreadyArchived.getHistoryAsList()).hasSize(historyAfterOwnArchive)
    }

    @Test
    fun `records the capacity and certification it stripped, so an unarchive can read it back`() {
      val cell = cell("001")
      val archiveTransaction = transaction()

      cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, archiveTransaction)

      assertThat(historyFor(cell, archiveTransaction, LocationAttribute.MAX_CAPACITY)).isEqualTo("2" to "0")
      assertThat(historyFor(cell, archiveTransaction, LocationAttribute.WORKING_CAPACITY)).isEqualTo("2" to "0")
      // the cell's own CNA, not the aggregate over cellLocations(), which is empty once the cell is archived
      assertThat(historyFor(cell, archiveTransaction, LocationAttribute.CERTIFIED_CAPACITY)).isEqualTo("2" to "0")
      assertThat(historyFor(cell, archiveTransaction, LocationAttribute.CERTIFICATION)).isEqualTo("Certified" to "Uncertified")
    }

    @Test
    fun `writes no certification history for a cell that was already uncertified`() {
      val cell = cell("001").apply { certifiedCell = false }
      val archiveTransaction = transaction()

      cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, archiveTransaction)

      assertThat(historyFor(cell, archiveTransaction, LocationAttribute.CERTIFICATION)).isNull()
    }
  }

  @Nested
  @DisplayName("Un-archiving a location")
  inner class UnArchivingALocation {

    @Test
    fun `restores the capacity and certification the cells held before the archive`() {
      val wing = wingWithTwoLandings()
      wing.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      wing.unarchive(DeactivatedReason.MOTHBALLED, null, "user", clock, transaction(TransactionType.REACTIVATION))

      assertThat(wing.findAllLeafLocations().filterIsInstance<Cell>()).allSatisfy { cell ->
        assertThat(cell.getMaxCapacity()).isEqualTo(2)
        assertThat(cell.getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
        assertThat(cell.getCertifiedNormalAccommodation()).isEqualTo(2)
        assertThat(cell.isCertified()).isTrue()
      }
    }

    @Test
    fun `restores the capacity the archive recorded, not the value before an earlier change`() {
      val cell = cell("001")
      cell.setCapacity(3, 3, 3, "user", LocalDateTime.now(clock), transaction(TransactionType.CAPACITY_CHANGE))
      cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      cell.unarchive(DeactivatedReason.MOTHBALLED, null, "user", clock, transaction(TransactionType.REACTIVATION))

      assertThat(cell.getMaxCapacity()).isEqualTo(3)
      assertThat(cell.getCurrentlyHeldWorkingCapacity()).isEqualTo(3)
      assertThat(cell.getCertifiedNormalAccommodation()).isEqualTo(3)
    }

    @Test
    fun `leaves a cell that was uncertified before the archive uncertified`() {
      val cell = cell("001").apply { certifiedCell = false }
      cell.permanentlyDeactivate("Demolished", LocalDateTime.now(clock), "user", clock, transaction())

      cell.unarchive(DeactivatedReason.MOTHBALLED, null, "user", clock, transaction(TransactionType.REACTIVATION))

      assertThat(cell.isCertified()).isFalse()
      assertThat(cell.getMaxCapacity()).isEqualTo(2)
    }

    @Test
    fun `restores a location archived before the archive recorded anything to the capacity it still holds`() {
      // Reproduces a location archived before MAPA-391: the status is ARCHIVED but nothing was stripped and no
      // capacity history was written, so the cell still holds its pre-archive values.
      val cell = cell("001")
      cell.status = LocationStatus.ARCHIVED

      cell.unarchive(DeactivatedReason.MOTHBALLED, null, "user", clock, transaction(TransactionType.REACTIVATION))

      assertThat(cell.getMaxCapacity()).isEqualTo(2)
      assertThat(cell.getCurrentlyHeldWorkingCapacity()).isEqualTo(2)
      assertThat(cell.getCertifiedNormalAccommodation()).isEqualTo(2)
      assertThat(cell.isCertified()).isTrue()
    }
  }

  private fun transaction(type: TransactionType = TransactionType.PERMANENT_DEACTIVATION) = LinkedTransaction(
    transactionId = UUID.randomUUID(),
    prisonId = "MDI",
    transactionType = type,
    transactionDetail = "TEST",
    transactionInvokedBy = "user",
    txStartTime = LocalDateTime.now(clock),
  )

  private fun historyFor(cell: Cell, transaction: LinkedTransaction, attribute: LocationAttribute) = cell.getHistoryAsList()
    .firstOrNull { it.attributeName == attribute && it.linkedTransaction?.transactionId == transaction.transactionId }
    ?.let { it.oldValue to it.newValue }

  /** An inactive wing of two landings, three certified 2/2/2 cells on each - the shape an archive is applied to. */
  private fun wingWithTwoLandings(): ResidentialLocation {
    val wing = ResidentialLocation(
      id = UUID.randomUUID(),
      code = "A",
      prisonId = "MDI",
      locationType = LocationType.WING,
      status = LocationStatus.INACTIVE,
      pathHierarchy = "A",
      createdBy = "user",
      whenCreated = LocalDateTime.now(clock),
      childLocations = sortedSetOf(),
    )
    listOf("1", "2").forEach { landingCode ->
      val landing = landing(landingCode)
      (1..3).forEach { landing.addChildLocation(cell("00$it")) }
      wing.addChildLocation(landing)
    }
    return wing
  }

  private fun landing(code: String) = ResidentialLocation(
    id = UUID.randomUUID(),
    code = code,
    prisonId = "MDI",
    locationType = LocationType.LANDING,
    status = LocationStatus.INACTIVE,
    pathHierarchy = code,
    createdBy = "user",
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
  )

  private fun cell(code: String) = Cell(
    id = UUID.randomUUID(),
    code = code,
    prisonId = "MDI",
    locationType = LocationType.CELL,
    status = LocationStatus.INACTIVE,
    pathHierarchy = code,
    createdBy = "user",
    whenCreated = LocalDateTime.now(clock),
    childLocations = sortedSetOf(),
    capacity = Capacity(maxCapacity = 2, workingCapacity = 2, certifiedNormalAccommodation = 2),
    certifiedCell = true,
  )

  private fun deactivationApprovalRequest(location: ResidentialLocation) = DeactivationApprovalRequest(
    location = location,
    requestedBy = "user",
    requestedDate = LocalDateTime.now(clock),
    workingCapacityChange = -1,
    deactivatedReason = DeactivatedReason.DAMAGED,
  )
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
  childLocations = sortedSetOf(),
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
  childLocations = sortedSetOf(),
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
  childLocations = sortedSetOf(),
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
