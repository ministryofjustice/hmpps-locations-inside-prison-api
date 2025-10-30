package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class NonResidentialServiceTest {
  private val sharedLocationService: SharedLocationService = mock()
  private val nonResidentialLocationRepository: NonResidentialLocationRepository = mock()
  private val linkedTransactionRepository: LinkedTransactionRepository = mock()
  private val clock: Clock = TestBase.clock
  private val authenticationHolder: HmppsAuthenticationHolder = mock()

  private val service = NonResidentialService(
    locationRepository = mock(),
    nonResidentialLocationRepository,
    sharedLocationService,
    clock,
  )

  @BeforeEach
  fun setUp() {
    whenever(authenticationHolder.username).thenReturn("User 1")
    whenever(linkedTransactionRepository.save(any())).thenReturn(mock())
  }

  // findAllByPrisonIdAndNonResidentialUsages
  @Test
  fun `should format local name`() {
    val prisonLocation = buildLocation("BULLINGDON (HMP)")
    whenever(nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(any(), any())).thenReturn(
      listOf(prisonLocation),
    )

    val nonResLoc =
      service.getByPrisonAndUsageType(
        "prisonId",
        NonResidentialUsageType.OCCURRENCE,
        sortByLocalName = false,
        formatLocalName = true,
      )
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("Bullingdon (HMP)")
  }

  private var location1 = buildLocation("A")
  private var location2 = buildLocation("B")
  private var location3 = buildLocation("CC")

  @Test
  fun `should sort by localName`() {
    val locations = listOf(location3, location1, location2)

    whenever(nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(any(), any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonAndUsageType(
        "prisonId",
        NonResidentialUsageType.OCCURRENCE,
        sortByLocalName = true,
        formatLocalName = false,
      )
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("B")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("CC")
  }

  @Test
  fun `should not sort by localName`() {
    val locations = listOf(location3, location2, location1)

    whenever(nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(any(), any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonAndUsageType("prisonId", NonResidentialUsageType.OCCURRENCE)
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("CC")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("B")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("A")
  }

  @Test
  fun `should sort by localName and format localName`() {
    val locations = listOf(location3, location2, location1)

    whenever(nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(any(), any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonAndUsageType(
        "prisonId",
        NonResidentialUsageType.OCCURRENCE,
        sortByLocalName = true,
        formatLocalName = true,
      )
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("B")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("Cc")
  }

  private fun buildLocation(localName: String): NonResidentialLocation = NonResidentialLocation(
    id = UUID.randomUUID(),
    localName = localName,
    code = "code",
    pathHierarchy = "path-a",
    locationType = LocationType.LOCATION,
    prisonId = "prisonId",
    status = LocationStatus.ACTIVE,
    whenCreated = LocalDateTime.now(),
    childLocations = mutableListOf(),
    createdBy = "createdBy",
  )
}
