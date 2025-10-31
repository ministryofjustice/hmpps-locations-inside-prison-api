package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityManager
import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellAttributes
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationLocalNameRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.CellLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.ResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationPrefixNotFoundException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

class LocationServiceTest {
  private val sharedLocationService: SharedLocationService = mock()
  private val locationRepository: LocationRepository = mock()
  private val residentialLocationRepository: ResidentialLocationRepository = mock()
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository = mock()
  private val cellLocationRepository: CellLocationRepository = mock()
  private val linkedTransactionRepository: LinkedTransactionRepository = mock()
  private val prisonerLocationService: PrisonerLocationService = mock()
  private val prisonService: PrisonService = mock()
  private val entityManager: EntityManager = mock()
  private val clock: Clock = TestBase.clock
  private val telemetryClient: TelemetryClient = mock()
  private val authenticationHolder: HmppsAuthenticationHolder = mock()
  private val locationGroupFromPropertiesService: LocationGroupFromPropertiesService = mock()
  private val activePrisonService: ActivePrisonService = mock()
  private val groupsProperties: Properties = mock()

  private val service = LocationService(
    sharedLocationService,
    locationRepository,
    residentialLocationRepository,
    signedOperationCapacityRepository,
    cellLocationRepository,
    linkedTransactionRepository,
    entityManager,
    prisonerLocationService,
    prisonService,
    clock,
    telemetryClient,
    locationGroupFromPropertiesService,
    activePrisonService,
    groupsProperties,
  )

  @BeforeEach
  fun setUp() {
    whenever(authenticationHolder.username).thenReturn("User 1")
    whenever(linkedTransactionRepository.save(any())).thenReturn(mock())
    whenever(sharedLocationService.createLinkedTransaction(eq("MDI"), any(), any(), anyOrNull())).thenReturn(
      LinkedTransaction(
        UUID.randomUUID(),
        TransactionType.LOCATION_CREATE,
        "MDI",
        "A Transaction",
        "User 1",
        LocalDateTime.now(),
      ),
    )
  }

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
  fun `Local name of wing can be changed`() {
    val updateLocationLocalNameRequest = UpdateLocationLocalNameRequest("L23", "User 1")
    val location =
      ResidentialLocation(
        id = UUID.randomUUID(),
        code = "code",
        pathHierarchy = "ph",
        locationType = LocationType.WING,
        prisonId = "MDI",
        parent = null,
        localName = "R23",
        comments = "comment 1",
        orderWithinParentLocation = 2,
        status = LocationStatus.ACTIVE,
        deactivatedDate = null,
        deactivatedReason = null,
        proposedReactivationDate = null,
        createdBy = "User 1 ",
        childLocations = mutableListOf(),
        whenCreated = LocalDateTime.now(clock),
      )
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(location))

    val locationDto = service.updateLocalName(UUID.randomUUID(), updateLocationLocalNameRequest)
    Assertions.assertThat(locationDto.localName).isEqualTo("L23")
  }

  @Test
  fun `Can not set local name for cell`() {
    val updateLocationLocalNameRequest = UpdateLocationLocalNameRequest("L23", "User 1")
    val location =
      Cell(
        id = UUID.randomUUID(),
        code = "code",
        cellMark = "code",
        pathHierarchy = "ph",
        prisonId = "MDI",
        parent = null,
        comments = "comment 1",
        orderWithinParentLocation = 2,
        status = LocationStatus.ACTIVE,
        deactivatedDate = null,
        deactivatedReason = null,
        proposedReactivationDate = null,
        createdBy = "User 1 ",
        childLocations = mutableListOf(),
        whenCreated = LocalDateTime.now(clock),
      )
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(location))

    val locationDto = service.updateLocalName(UUID.randomUUID(), updateLocationLocalNameRequest)
    Assertions.assertThat(locationDto.localName).isEqualTo(null)
  }

  @Test
  fun `should return location prefix for group`() {
    whenever(groupsProperties.getProperty(any())).thenReturn("MDI-2-")

    val locationPrefixDto = service.getLocationPrefixFromGroup("MDI", "Houseblock 7")

    Assertions.assertThat(locationPrefixDto.locationPrefix).isEqualTo("MDI-2-")
  }

  // getLocationById
  @Test
  fun `should format local name correctly for a prison`() {
    val prisonLocation = buildLocation("BULLINGDON (HMP)")
    whenever(locationRepository.findById(any())).thenReturn(
      Optional.of(prisonLocation),
    )

    val loc = service.getLocationById(UUID.randomUUID(), formatLocalName = true)
    Assertions.assertThat(loc?.localName).isEqualTo("Bullingdon (HMP)")
  }

  @Test
  fun `should format local name correctly for a room`() {
    val cellLocation = buildLocation("ROOM ONE")
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(cellLocation))

    val loc = service.getLocationById(UUID.randomUUID(), formatLocalName = true)
    Assertions.assertThat(loc?.localName).isEqualTo("Room One")
  }

  @Test
  fun `should not format local name`() {
    val cellLocation = buildLocation("CeLL a")
    whenever(locationRepository.findById(any())).thenReturn(Optional.of(cellLocation))

    val loc = service.getLocationById(UUID.randomUUID())
    Assertions.assertThat(loc?.localName).isEqualTo("CeLL a")
  }

  // getLocationByPrisonAndLocationType
  private val adjRoom1 = buildLocation("A-ADJ-1")
  private val adjRoom2 = buildLocation("A-ADJ-2")
  private val adjRoom3 = buildLocation("A-ADJ-3")

  @Test
  fun `should sort rooms by localName and also format localName`() {
    whenever(locationRepository.findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(any(), any())).thenReturn(
      listOf(adjRoom3, adjRoom1, adjRoom2),
    )

    val nonResLoc =
      service.getLocationByPrisonAndLocationType(
        "MDI",
        LocationType.ADJUDICATION_ROOM,
        sortByLocalName = true,
        formatLocalName = true,
      )

    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A-adj-1")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("A-adj-2")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("A-adj-3")
  }

  @Test
  fun `should sort rooms by localName but not format localName`() {
    whenever(locationRepository.findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(any(), any())).thenReturn(
      listOf(adjRoom3, adjRoom1, adjRoom2),
    )

    val nonResLoc =
      service.getLocationByPrisonAndLocationType(
        "MDI",
        LocationType.ADJUDICATION_ROOM,
        sortByLocalName = true,
        formatLocalName = false,
      )

    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A-ADJ-1")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("A-ADJ-2")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("A-ADJ-3")
  }

  @Test
  fun `should not sort rooms by localName or format localName`() {
    whenever(locationRepository.findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(any(), any())).thenReturn(
      listOf(adjRoom3, adjRoom1, adjRoom2),
    )

    val nonResLoc =
      service.getLocationByPrisonAndLocationType(
        "MDI",
        LocationType.ADJUDICATION_ROOM,
        sortByLocalName = false,
        formatLocalName = false,
      )

    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A-ADJ-3")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("A-ADJ-1")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("A-ADJ-2")
  }

  // getLocationPrefixFromGroup
  @Test
  fun `should throw correct exception when location prefix not found`() {
    whenever(groupsProperties.getProperty(ArgumentMatchers.anyString())).thenReturn(null)

    Assertions.assertThatExceptionOfType(LocationPrefixNotFoundException::class.java).isThrownBy {
      service.getLocationPrefixFromGroup("XXX", "1")
    }
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

  // getCellAttributes
  @Test
  fun `should return no attributes if none set`() {
    val mockCell: Cell = mock()

    whenever(mockCell.specialistCellTypes).thenReturn(mutableSetOf())
    whenever(cellLocationRepository.findById(any())).thenReturn(Optional.of(mockCell))
    whenever(activePrisonService.isActivePrison(any())).thenReturn(true)

    val attributes = service.getCellAttributes(UUID.randomUUID())
    Assertions.assertThat(attributes).isEqualTo(emptyList<CellAttributes>())
  }

  @Test
  fun `should not return specialist type attributes if prison inactive`() {
    val location: Location = mock()
    val specialistCellType: SpecialistCellType = SpecialistCellType.CAT_A
    val mockCell: Cell = mock()

    whenever(mockCell.specialistCellTypes).thenReturn(mutableSetOf(SpecialistCell(1, location, specialistCellType)))
    whenever(cellLocationRepository.findById(any())).thenReturn(Optional.of(mockCell))
    whenever(activePrisonService.isActivePrison(any())).thenReturn(false)

    val attributes = service.getCellAttributes(UUID.randomUUID())
    Assertions.assertThat(attributes).isEqualTo(emptyList<CellAttributes>())
  }

  @Test
  fun `should return specialist type attributes for cell when only specialist types present`() {
    val location: Location = mock()
    val specialistCellType: SpecialistCellType = SpecialistCellType.CAT_A
    val mockCell: Cell = mock()

    whenever(mockCell.specialistCellTypes).thenReturn(mutableSetOf(SpecialistCell(1, location, specialistCellType)))
    whenever(cellLocationRepository.findById(any())).thenReturn(Optional.of(mockCell))
    whenever(mockCell.prisonId).thenReturn("MDI")
    whenever(activePrisonService.isActivePrison(any())).thenReturn(true)

    val attributes = service.getCellAttributes(UUID.randomUUID())
    Assertions.assertThat(attributes).isEqualTo(mutableListOf(CellAttributes(code = SpecialistCellType.CAT_A, description = SpecialistCellType.CAT_A.description)))
  }

  @Test
  fun `should return residential type attributes for cell when only residential types present`() {
    val location: Location = mock()
    val residentialCellType: ResidentialAttributeType = ResidentialAttributeType.LOCATION_ATTRIBUTE
    val residentialCellValue: ResidentialAttributeValue = ResidentialAttributeValue.CAT_A_CELL
    val mockCell: Cell = mock()

    whenever(mockCell.attributes).thenReturn(mutableSetOf(ResidentialAttribute(1, location, residentialCellType, residentialCellValue)))
    whenever(cellLocationRepository.findById(any())).thenReturn(Optional.of(mockCell))
    whenever(activePrisonService.isActivePrison(any())).thenReturn(false)

    val attributes = service.getCellAttributes(UUID.randomUUID())
    Assertions.assertThat(attributes).isEqualTo(mutableListOf(CellAttributes(code = residentialCellValue, description = residentialCellValue.description)))
  }

  @Test
  fun `should return specialist type attributes for cell when both specialist and residential types present`() {
    val location: Location = mock()
    val specialistCellType: SpecialistCellType = SpecialistCellType.CAT_A
    val residentialCellType: ResidentialAttributeType = ResidentialAttributeType.LOCATION_ATTRIBUTE
    val residentialCellValue: ResidentialAttributeValue = ResidentialAttributeValue.CAT_A_CELL
    val mockCell: Cell = mock()

    whenever(mockCell.specialistCellTypes).thenReturn(mutableSetOf(SpecialistCell(1, location, specialistCellType)))
    whenever(mockCell.attributes).thenReturn(mutableSetOf(ResidentialAttribute(1, location, residentialCellType, residentialCellValue)))
    whenever(cellLocationRepository.findById(any())).thenReturn(Optional.of(mockCell))
    whenever(mockCell.prisonId).thenReturn("MDI")
    whenever(activePrisonService.isActivePrison(any())).thenReturn(true)

    val attributes = service.getCellAttributes(UUID.randomUUID())
    Assertions.assertThat(attributes).isEqualTo(mutableListOf(CellAttributes(code = SpecialistCellType.CAT_A, description = SpecialistCellType.CAT_A.description)))
  }

  // getPrisonResidentialHierarchy
  @Test
  fun `should return non perm disabled locations when includeInactive is true`() {
    val prisonId = "MDI"
    val locations = createTestLocations()

    whenever(residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)).thenReturn(locations)

    val result = service.getPrisonResidentialHierarchy(prisonId = prisonId, includeInactive = true)
    Assertions.assertThat(result).hasSize(2)
    Assertions.assertThat(result[0].status).isEqualTo(LocationStatus.ACTIVE)
    Assertions.assertThat(result[1].status).isEqualTo(LocationStatus.INACTIVE)
  }

  @Test
  fun `should not return non perm disabled locations when includeInactive is false`() {
    val prisonId = "MDI"
    val locations = createTestLocations()

    whenever(residentialLocationRepository.findAllByPrisonIdAndParentIsNull(prisonId)).thenReturn(locations)

    val result = service.getPrisonResidentialHierarchy(prisonId = prisonId, includeInactive = false)
    Assertions.assertThat(result).hasSize(1)
    Assertions.assertThat(result[0].status).isEqualTo(LocationStatus.ACTIVE)
  }

  private fun createTestLocations(): List<ResidentialLocation> = listOf(
    ResidentialLocation(
      id = UUID.randomUUID(),
      code = "R1",
      pathHierarchy = "PH1",
      locationType = LocationType.WING,
      prisonId = "MDI",
      parent = null,
      localName = "Residential 1",
      comments = "Test comment 1",
      orderWithinParentLocation = 1,
      status = LocationStatus.ACTIVE,
      deactivatedDate = LocalDateTime.now(),
      proposedReactivationDate = null,
      createdBy = "Test User",
      childLocations = mutableListOf(),
      whenCreated = LocalDateTime.now(),
    ),
    ResidentialLocation(
      id = UUID.randomUUID(),
      code = "R2",
      pathHierarchy = "PH2",
      locationType = LocationType.WING,
      prisonId = "MDI",
      parent = null,
      localName = "Residential 2",
      comments = "Test comment 2",
      orderWithinParentLocation = 2,
      status = LocationStatus.INACTIVE,
      deactivatedDate = LocalDateTime.now(),
      proposedReactivationDate = null,
      createdBy = "Test User",
      childLocations = mutableListOf(),
      whenCreated = LocalDateTime.now(),
    ),
  )
}
