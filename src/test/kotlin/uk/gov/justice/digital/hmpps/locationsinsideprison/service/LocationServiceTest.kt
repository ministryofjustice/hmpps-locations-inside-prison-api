package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationHistoryRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonSignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class LocationServiceTest {
  private val locationRepository: LocationRepository = mock()
  private val nonResidentialLocationRepository: NonResidentialLocationRepository = mock()
  private val residentialLocationRepository: ResidentialLocationRepository = mock()
  private val signedOperationCapacityRepository: PrisonSignedOperationCapacityRepository = mock()
  private val locationHistoryRepository: LocationHistoryRepository = mock()
  private val cellLocationRepository: CellLocationRepository = mock()
  private val prisonerLocationService: PrisonerLocationService = mock()
  private val clock: Clock = TestBase.clock
  private val telemetryClient: TelemetryClient = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService = mock()

  private val service = LocationService(
    locationRepository,
    nonResidentialLocationRepository,
    residentialLocationRepository,
    signedOperationCapacityRepository,
    locationHistoryRepository,
    cellLocationRepository,
    prisonerLocationService,
    clock,
    telemetryClient,
    authenticationFacade,
    locationGroupFromPropertiesService,
  )

  @Test
  fun `when update location and location not found throw LocationNotFoundException`() {
    val updateLocationRequest = UpdateLocationRequest("L23", "comment", "User 1")
    whenever(locationRepository.findById(any())).thenReturn(Optional.empty())
    Assertions.assertThatExceptionOfType(LocationNotFoundException::class.java).isThrownBy {
      service.updateLocation(UUID.randomUUID(), updateLocationRequest)
    }
  }

  @Test
  fun `when update location and location is permanently deactivated throw ValidationException`() {
    val updateLocationRequest = UpdateLocationRequest("L23", "comment", "User 1")
    val location: Location = mock()
    whenever(location.isPermanentlyDeactivated()).thenReturn(true)
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(location))
    Assertions.assertThatExceptionOfType(ValidationException::class.java).isThrownBy {
      service.updateLocation(UUID.randomUUID(), updateLocationRequest)
    }
  }

  @Test
  fun `update location`() {
    val updateLocationRequest = UpdateLocationRequest("L23", "additional comment", "User 1")
    val location =
      Cell(
        id = UUID.randomUUID(),
        code = "code",
        pathHierarchy = "ph",
        locationType = LocationType.LOCATION,
        prisonId = "MDI",
        parent = null,
        localName = "R23",
        comments = "comment 1",
        orderWithinParentLocation = 2,
        active = true,
        deactivatedDate = null,
        deactivatedReason = null,
        proposedReactivationDate = null,
        createdBy = "User 1 ",
        childLocations = mutableListOf(),
        whenCreated = LocalDateTime.now(clock),
      )
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(location))

    val cellDto = service.updateLocation(UUID.randomUUID(), updateLocationRequest)
    Assertions.assertThat(cellDto.comments).isEqualTo("additional comment")
    Assertions.assertThat(cellDto.localName).isEqualTo("L23")
  }
}
