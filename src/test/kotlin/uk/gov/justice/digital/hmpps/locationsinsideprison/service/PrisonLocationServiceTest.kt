package uk.gov.justice.digital.hmpps.locationsinsideprison.service


import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository

class PrisonLocationServiceTest {
  private val cellLocationRepository: CellLocationRepository = mock()
  private val locationRepository: LocationRepository = mock()
  private val prisonerSearchService: PrisonerSearchService = mock()
  private val service = PrisonerLocationService(cellLocationRepository, locationRepository, prisonerSearchService)

  @Test
  fun `Get prisoners by prison id when cell location exist and location is active`() {
    val cell: Cell = mock()
    val prisoner: Prisoner = mock()
    whenever(cell.getPathHierarchy()).thenReturn("path")
    whenever(cellLocationRepository.findAllByPrisonIdAndActive(any(), any())).thenReturn(listOf(cell))
    whenever(prisonerSearchService.findPrisonersInLocations(any(), any())).thenReturn(listOf(prisoner))
    val result = service.prisonersInPrison("MDI")
  }
}
