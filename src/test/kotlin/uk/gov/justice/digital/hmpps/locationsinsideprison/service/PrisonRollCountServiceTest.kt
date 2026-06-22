package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import java.util.UUID

class PrisonRollCountServiceTest {

  private val residentialLocationRepository: ResidentialLocationRepository = mock()
  private val prisonerLocationService: PrisonerLocationService = mock()
  private val prisonApiService: PrisonApiService = mock()
  private val prisonConfigurationRepository: PrisonConfigurationRepository = mock()
  private val service = PrisonRollCountService(
    residentialLocationRepository,
    prisonerLocationService,
    prisonApiService,
    prisonConfigurationRepository,
    clock,
  )

  private fun prisoner(cell: String) = Prisoner(
    prisonerNumber = "A0000AA",
    prisonId = "LEI",
    prisonName = "HMP Leeds",
    cellLocation = cell,
    firstName = "Dave",
    lastName = "Jones",
    gender = "Male",
    status = "ACTIVE IN",
    inOutStatus = "IN",
  )

  private fun cell(
    code: String,
    cna: Int?,
    occupants: Int,
  ) = ResidentialPrisonerLocation(
    locationId = UUID.randomUUID(),
    key = "LEI-A-1-$code",
    locationType = LocationType.CELL,
    locationCode = code,
    fullLocationPath = "A-1-$code",
    certified = true,
    status = DerivedLocationStatus.ACTIVE,
    subLocations = emptyList(),
    capacity = Capacity(maxCapacity = 3, workingCapacity = cna ?: 0, certifiedNormalAccommodation = cna),
    prisoners = (1..occupants).map { prisoner("A-1-$code") },
    isLeafLevel = true,
  )

  private fun wing(cells: List<ResidentialPrisonerLocation>) = ResidentialPrisonerLocation(
    locationId = UUID.randomUUID(),
    key = "LEI-A",
    locationType = LocationType.WING,
    locationCode = "A",
    fullLocationPath = "A",
    status = DerivedLocationStatus.ACTIVE,
    subLocations = cells,
    isLeafLevel = false,
  )

  @Test
  fun `overcrowding totals for the worked example`() {
    // 5 cells each CNA 1: two hold 2, one holds 3, two hold 1 -> 3 overcrowded cells, total overcrowding 4
    val wing = wing(
      listOf(
        cell("001", cna = 1, occupants = 2),
        cell("002", cna = 1, occupants = 2),
        cell("003", cna = 1, occupants = 3),
        cell("004", cna = 1, occupants = 1),
        cell("005", cna = 1, occupants = 1),
      ),
    )

    assertThat(wing.getOvercrowdedCellCount()).isEqualTo(3)
    assertThat(wing.getTotalOvercrowding()).isEqualTo(4)

    val dto = wing.toDto(includeCells = true, filterSeg = false)
    assertThat(dto.overcrowded).isTrue()
    assertThat(dto.overcrowdedBy).isEqualTo(4)
    assertThat(dto.rollCount.cellsOvercrowded).isEqualTo(3)
    assertThat(dto.rollCount.totalOvercrowded).isEqualTo(4)
  }

  @Test
  fun `a single overcrowded cell reports its own flag and amount`() {
    val overcrowded = cell("001", cna = 1, occupants = 2)
    assertThat(overcrowded.getOvercrowdedCellCount()).isEqualTo(1)
    assertThat(overcrowded.getTotalOvercrowding()).isEqualTo(1)

    val dto = overcrowded.toDto(includeCells = true, filterSeg = false)
    assertThat(dto.overcrowded).isTrue()
    assertThat(dto.overcrowdedBy).isEqualTo(1)
    assertThat(dto.rollCount.cellsOvercrowded).isEqualTo(1)
    assertThat(dto.rollCount.totalOvercrowded).isEqualTo(1)
  }

  @Test
  fun `a cell at or below its CNA is not overcrowded`() {
    val atCapacity = cell("001", cna = 2, occupants = 2)
    val underCapacity = cell("002", cna = 2, occupants = 1)

    assertThat(atCapacity.getOvercrowdedCellCount()).isEqualTo(0)
    assertThat(atCapacity.getTotalOvercrowding()).isEqualTo(0)
    assertThat(underCapacity.getTotalOvercrowding()).isEqualTo(0)

    val dto = atCapacity.toDto(includeCells = true, filterSeg = false)
    assertThat(dto.overcrowded).isFalse()
    assertThat(dto.overcrowdedBy).isEqualTo(0)
  }

  @Test
  fun `a null CNA is treated as zero so any occupant is overcrowding`() {
    val noCna = cell("001", cna = null, occupants = 2)
    assertThat(noCna.getOvercrowdedCellCount()).isEqualTo(1)
    assertThat(noCna.getTotalOvercrowding()).isEqualTo(2)
  }

  @Test
  fun `Get duplicate count`() {
    val offenderMovements = listOf(
      OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
      OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = "2"),
      OffenderMovement(offenderNo = "A1006AA", movementType = "CRT", movementSequence = "1"),
      OffenderMovement(offenderNo = "A1006AA", movementType = "REL", movementSequence = "2"),
    )

    val doubleMoveCount = service.getConsecutiveOutMoveCount(offenderMovements)
    assertThat(doubleMoveCount).isEqualTo(2)
  }

  @Test
  fun `Get duplicate count when out of order out movement`() {
    val offenderMovements = listOf(
      OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = "1"),
      OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = "2"),
      OffenderMovement(offenderNo = "A1006AA", movementType = "CRT", movementSequence = "6"),
      OffenderMovement(offenderNo = "A1006AA", movementType = "REL", movementSequence = "8"),
    )

    val doubleMoveCount = service.getConsecutiveOutMoveCount(offenderMovements)
    assertThat(doubleMoveCount).isEqualTo(1)
  }

  @Test
  fun `Get duplicate count when out of order out movement and multiple offender sequences overlap`() {
    val offenderMovements = listOf(
      OffenderMovement(offenderNo = "G8395GQ", movementType = "TAP", movementSequence = "56"),
      OffenderMovement(offenderNo = "G8395GQ", movementType = "REL", movementSequence = "57"),
      OffenderMovement(offenderNo = "G6416UJ", movementType = "TAP", movementSequence = "4"),
      OffenderMovement(offenderNo = "G6416UJ", movementType = "TAP", movementSequence = "6"),
      OffenderMovement(offenderNo = "G3126VH", movementType = "CRT", movementSequence = "18"),
      OffenderMovement(offenderNo = "G3126VH", movementType = "TRN", movementSequence = "19"),
      OffenderMovement(offenderNo = "G1751UN", movementType = "TAP", movementSequence = "4"),
      OffenderMovement(offenderNo = "G1751UN", movementType = "REL", movementSequence = "5"),
    )

    val doubleMoveCount = service.getConsecutiveOutMoveCount(offenderMovements)
    assertThat(doubleMoveCount).isEqualTo(3)
  }

  @Test
  fun `Get duplicate count where movement sequence is null`() {
    val offenderMovements = listOf(
      OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = null),
      OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = null),
    )

    val doubleMoveCount = service.getConsecutiveOutMoveCount(offenderMovements)
    assertThat(doubleMoveCount).isEqualTo(0)
  }

  @Test
  fun `Get duplicate count where movement sequence is empty`() {
    val offenderMovements = listOf(
      OffenderMovement(offenderNo = "A1001AA", movementType = "CRT", movementSequence = ""),
      OffenderMovement(offenderNo = "A1001AA", movementType = "REL", movementSequence = ""),
    )

    val doubleMoveCount = service.getConsecutiveOutMoveCount(offenderMovements)
    assertThat(doubleMoveCount).isEqualTo(0)
  }

  @Test
  fun `get overnight count returns number of offender movements with OUT direction`() {
    whenever(prisonerLocationService.prisonerNumbersForOvernightCount("MDI")).thenReturn(listOf("A1000AA", "A1001AA", "A1002AA"))
    whenever(prisonApiService.getLatestMovementsForOffenders(listOf("A1000AA", "A1001AA", "A1002AA"))).thenReturn(
      listOf(
        LatestOffenderMovement(offenderNo = "A1000AA", directionCode = "OUT"),
        LatestOffenderMovement(offenderNo = "A1001AA", directionCode = "IN"),
        LatestOffenderMovement(offenderNo = "A1002AA", directionCode = "OUT"),
      ),
    )

    val overnightCount = service.getNumOvernights("MDI")

    assertThat(overnightCount).isEqualTo(2)
  }

  @Test
  fun `get overnight count does not call prison api when no offenders are eligible`() {
    whenever(prisonerLocationService.prisonerNumbersForOvernightCount("MDI")).thenReturn(emptyList())

    val overnightCount = service.getNumOvernights("MDI")

    assertThat(overnightCount).isEqualTo(0)
    verify(prisonApiService, never()).getLatestMovementsForOffenders(org.mockito.kotlin.any())
  }
}
