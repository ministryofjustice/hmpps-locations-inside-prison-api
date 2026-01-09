package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PrisonRollCountServiceTest {

  private val service = PrisonRollCountService(mock(), mock(), mock(), mock())

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
}
