package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityManager
import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationPrefixNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade
import java.time.Clock
import java.time.LocalDateTime
import java.util.Optional
import java.util.Properties
import java.util.UUID

class LocationServiceTest {
  private val locationRepository: LocationRepository = mock()
  private val nonResidentialLocationRepository: NonResidentialLocationRepository = mock()
  private val residentialLocationRepository: ResidentialLocationRepository = mock()
  private val signedOperationCapacityRepository: PrisonSignedOperationCapacityRepository = mock()
  private val locationHistoryRepository: LocationHistoryRepository = mock()
  private val cellLocationRepository: CellLocationRepository = mock()
  private val prisonerLocationService: PrisonerLocationService = mock()
  private val prisonService: PrisonService = mock()
  private val entityManager: EntityManager = mock()
  private val clock: Clock = TestBase.clock
  private val telemetryClient: TelemetryClient = mock()
  private val authenticationFacade: AuthenticationFacade = mock()
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService = mock()
  private val groupsProperties: Properties = mock()

  private val service = LocationService(
    locationRepository,
    nonResidentialLocationRepository,
    residentialLocationRepository,
    signedOperationCapacityRepository,
    locationHistoryRepository,
    cellLocationRepository,
    entityManager,
    prisonerLocationService,
    prisonService,
    clock,
    telemetryClient,
    authenticationFacade,
    locationGroupFromPropertiesService,
    groupsProperties,
  )

  @Test
  fun `when update location and location not found throw LocationNotFoundException`() {
    val updateLocationLocalNameRequest = UpdateLocationLocalNameRequest("L23", "User 1")
    whenever(locationRepository.findById(any())).thenReturn(Optional.empty())
    Assertions.assertThatExceptionOfType(LocationNotFoundException::class.java).isThrownBy {
      service.updateLocalName(UUID.randomUUID(), updateLocationLocalNameRequest)
    }
  }

  @Test
  fun `when update location and location is permanently deactivated throw ValidationException`() {
    val updateLocationLocalNameRequest = UpdateLocationLocalNameRequest("L23", "User 1")
    val location: Location = mock()
    whenever(location.getKey()).thenReturn("L23-A-1-001")
    whenever(location.isPermanentlyDeactivated()).thenReturn(true)
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(location))
    Assertions.assertThatExceptionOfType(ValidationException::class.java).isThrownBy {
      service.updateLocalName(UUID.randomUUID(), updateLocationLocalNameRequest)
    }
  }

  @Test
  fun `update location`() {
    val updateLocationLocalNameRequest = UpdateLocationLocalNameRequest("L23", "User 1")
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

    val cellDto = service.updateLocalName(UUID.randomUUID(), updateLocationLocalNameRequest)
    Assertions.assertThat(cellDto.localName).isEqualTo("L23")
  }

  @Test
  fun `should return location prefix for group`() {
    whenever(groupsProperties.getProperty(any())).thenReturn("MDI-2-")

    val locationPrefixDto = service.getLocationPrefixFromGroup("MDI", "Houseblock 7")

    Assertions.assertThat(locationPrefixDto.locationPrefix).isEqualTo("MDI-2-")
  }

  @Test
  fun `should throw correct exception when location prefix not found`() {
    whenever(groupsProperties.getProperty(ArgumentMatchers.anyString())).thenReturn(null)

    Assertions.assertThatExceptionOfType(LocationPrefixNotFoundException::class.java).isThrownBy {
      service.getLocationPrefixFromGroup("XXX", "1")
    }
  }
}
