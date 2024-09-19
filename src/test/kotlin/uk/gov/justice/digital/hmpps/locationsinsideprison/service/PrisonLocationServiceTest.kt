package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import java.util.*

class PrisonLocationServiceTest {
  private val cellLocationRepository: CellLocationRepository = mock()
  private val locationRepository: LocationRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val service = PrisonerLocationService(cellLocationRepository, locationRepository, prisonerSearchService)

  @Test
  fun `Get prisoners by prison id when cell location exist and location is active`() {
    val cell1: Cell = mock()
    val cell2: Cell = mock()
    val prisoner1 = Prisoner(prisonerNumber = "P1", firstName = "First Name", lastName = "Last Name", prisonId = "A", prisonName = "Prison Name", cellLocation = "C1", gender = "MALE", inOutStatus = "IN", status = "ACTIVE")
    val prisoner2 = Prisoner(prisonerNumber = "P2", firstName = "First Name", lastName = "Last Name", prisonId = "B", prisonName = "Prison Name", cellLocation = "C2", gender = "MALE", inOutStatus = "IN", status = "ACTIVE")
    val prisoner3 = Prisoner(prisonerNumber = "P3", firstName = "First Name", lastName = "Last Name", prisonId = "B", prisonName = "Prison Name", cellLocation = "C2", gender = "MALE", inOutStatus = "IN", status = "ACTIVE")
    whenever(cell1.getPathHierarchy()).thenReturn("A")
    whenever(cell2.getPathHierarchy()).thenReturn("B")
    whenever(cellLocationRepository.findAllByPrisonIdAndActive(any(), any())).thenReturn(listOf(cell1, cell2))
    whenever(prisonerSearchService.findPrisonersInLocations(any(), any(), any())).thenReturn(
      listOf(
        prisoner1,
        prisoner2,
        prisoner3,
      ),
    )
    val result = service.prisonersInPrison("MDI")
    assertThat(result.size).isEqualTo(2)
    assertThat(result[0].prisoners).contains(prisoner1)
    assertThat(result[1].prisoners).contains(prisoner2, prisoner3)
  }

  @Test
  fun `Get prisoners by prison id when cell location exist and location is active and the location is empty`() {
    whenever(cellLocationRepository.findAllByPrisonIdAndActive(any(), any())).thenReturn(listOf())
    whenever(prisonerSearchService.findPrisonersInLocations(any(), any(), any())).thenReturn(
      listOf(),
    )
    val result = service.prisonersInPrison("MDI")
    assertThat(result.size).isEqualTo(0)
  }

  @Test
  fun `Get prisoners in location by key`() {
    val cell: Cell = mock()
    whenever(cell.getPathHierarchy()).thenReturn("path")
    whenever(cell.cellLocations()).thenReturn(listOf(cell))
    whenever(cell.prisonId).thenReturn("MDI")
    val prisoner = Prisoner(
      prisonerNumber = "P1",
      prisonId = "First Name",
      prisonName = "Last Name",
      cellLocation = "MDI",
      firstName = "Prison Name",
      lastName = "C1",
      gender = "MALE",
      inOutStatus = "IN",
      status = "ACTIVE",
    )

    whenever(locationRepository.findOneByKey(any())).thenReturn(cell)

    whenever(prisonerSearchService.findPrisonersInLocations(any(), any(), any())).thenReturn(
      listOf(prisoner),
    )
    val result = service.prisonersInLocations("C1")
    assertThat(result[0].prisoners[0]).isEqualTo(prisoner)
  }

  @Test
  fun `Throw LocationNotFoundException when search location by key has no results`() {
    whenever(locationRepository.findOneByKey(any())).thenReturn(null)
    assertThatExceptionOfType(LocationNotFoundException::class.java).isThrownBy {
      service.prisonersInLocations("C1")
    }
  }

  @Test
  fun `Get prisoners in location id with alert`() {
    val cell: Cell = mock()
    val alerts = listOf(Alert("X", "XA", true, false), Alert("X", "XA", true, false))

    whenever(cell.getPathHierarchy()).thenReturn("path")
    whenever(cell.cellLocations()).thenReturn(listOf(cell))
    whenever(cell.prisonId).thenReturn("MDI")
    val prisoner = Prisoner(
      prisonerNumber = "P1",
      prisonId = "First Name",
      prisonName = "Last Name",
      cellLocation = "MDI",
      firstName = "Prison Name",
      lastName = "C1",
      gender = "MALE",
      inOutStatus = "IN",
      status = "ACTIVE",
      alerts = alerts,
    )

    whenever(locationRepository.findById(any())).thenReturn(Optional.of(cell))

    whenever(prisonerSearchService.findPrisonersInLocations(any(), any(), any())).thenReturn(
      listOf(prisoner),
    )
    val result = service.prisonersInLocations(UUID.randomUUID())
    assertThat(result[0].prisoners[0]).isEqualTo(prisoner)
  }

  @Test
  fun `Throw LocationNotFoundException when search location by id has no results`() {
    whenever(locationRepository.findById(any())).thenReturn(Optional.empty())
    assertThatExceptionOfType(LocationNotFoundException::class.java).isThrownBy {
      service.prisonersInLocations(UUID.randomUUID())
    }
  }
}
