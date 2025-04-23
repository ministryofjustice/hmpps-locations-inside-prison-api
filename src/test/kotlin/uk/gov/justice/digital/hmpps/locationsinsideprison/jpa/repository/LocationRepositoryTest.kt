package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationResidentialResource.AllowedAccommodationTypeForConversion
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class LocationRepositoryTest : TestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @Autowired
  lateinit var linkedTransactionRepository: LinkedTransactionRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  fun findCellsOnAWingTest() {
    val linkedTransaction = linkedTransactionRepository.saveAndFlush(
      LinkedTransaction(
        prisonId = "MDI",
        transactionInvokedBy = EXPECTED_USERNAME,
        transactionType = TransactionType.LOCATION_CREATE,
        transactionDetail = "TEST",
        txStartTime = LocalDateTime.now(clock),
        txEndTime = LocalDateTime.now(clock),
      ),
    )

    val wing = buildResLocation("A", locationType = LocationType.WING)
    val landing1 = buildResLocation("A-1", locationType = LocationType.LANDING)
    val landing2 = buildResLocation("A-2", locationType = LocationType.LANDING)
    val cell001L1 = buildCell(
      "A-1-001",
      residentialAttributeValues = setOf(ResidentialAttributeValue.AUDITABLE_CELL_BELL, ResidentialAttributeValue.SAFE_CELL),
    )
    val cell002L1 = buildCell(
      "A-1-002",
      residentialAttributeValues = setOf(ResidentialAttributeValue.SENTENCED_ADULTS, ResidentialAttributeValue.SAFE_CELL),
    )
    val cell002L2 = buildCell(
      "A-2-001",
      residentialAttributeValues = setOf(ResidentialAttributeValue.ANTI_BARRICADE_DOOR, ResidentialAttributeValue.SAFE_CELL),
    )
    val adjRoom = buildNonResLocation("A-ADJ", locationType = LocationType.ADJUDICATION_ROOM, status = LocationStatus.ACTIVE)
    wing.addChildLocation(landing1)
    wing.addChildLocation(landing2)
    wing.addChildLocation(adjRoom)
    landing1.addChildLocation(cell001L1)
    landing1.addChildLocation(cell002L1)
    landing2.addChildLocation(cell002L2)
    repository.save(wing)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val location = repository.findOneByPrisonIdAndPathHierarchy("MDI", "A") ?: throw Exception("Location not found")

    location.findAllLeafLocations().forEach {
      if (it is Cell) {
        assertThat(it.getWorkingCapacity()).isEqualTo(1)
        assertThat(it.getCapacityOfCertifiedCell()).isEqualTo(1)
      }
    }

    assertThat(location.findAllLeafLocations()).containsExactlyInAnyOrder(cell001L1, cell002L1, cell002L2, adjRoom)
    location.findAllLeafLocations().forEach {
      if (it is Cell) {
        it.setCapacity(workingCapacity = 2, maxCapacity = 2, userOrSystemInContext = "test", clock = clock, linkedTransaction = linkedTransaction)
        it.certifyCell(userOrSystemInContext = "test", clock = clock, linkedTransaction = linkedTransaction)
      }
    }

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell2 = repository.findOneByPrisonIdAndPathHierarchy(cell002L2.prisonId, cell002L2.getPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell2.findTopLevelLocation()).isEqualTo(wing)
    assertThat(cell2.getPathHierarchy()).isEqualTo("A-2-001")
    cell2 as Cell
    assertThat(cell2.getWorkingCapacity()).isEqualTo(2)
    assertThat(cell2.getCapacityOfCertifiedCell()).isEqualTo(1)

    val landing1Retrieved = repository.findOneByPrisonIdAndPathHierarchy(landing1.prisonId, landing1.getPathHierarchy()) ?: throw Exception("Location not found")
    cell2.setCode("003")
    cell2.setParent(landing1Retrieved)

    repository.save(landing1Retrieved)
    repository.save(cell2)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3 = repository.findOneByPrisonIdAndPathHierarchy(cell2.prisonId, cell2.getPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3.findTopLevelLocation()).isEqualTo(wing)
    assertThat(cell3.getParent()?.getCode()).isEqualTo(landing1.getCode())

    cell3.getParent()?.getParent()?.setCode("T")
    repository.save(cell3)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3Renamed = repository.findOneByPrisonIdAndPathHierarchy(cell3.prisonId, cell3.getPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3Renamed.getPathHierarchy()).isEqualTo("T-1-003")

    (cell3Renamed as Cell).convertToNonResidentialCell(convertedCellType = ConvertedCellType.HOLDING_ROOM, userOrSystemInContext = "test", clock = clock, linkedTransaction = linkedTransaction)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3Converted = repository.findOneByPrisonIdAndPathHierarchy(cell3.prisonId, cell3.getPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3Converted.getDerivedStatus()).isEqualTo(DerivedLocationStatus.NON_RESIDENTIAL)
    assertThat((cell3Converted as Cell).getMaxCapacity()).isNull()
    assertThat(cell3Converted.isCertified()).isFalse()

    cell3Converted.convertToCell(
      accommodationType = AllowedAccommodationTypeForConversion.NORMAL_ACCOMMODATION,
      usedForTypes = listOf(UsedForType.FIRST_NIGHT_CENTRE, UsedForType.MOTHER_AND_BABY),
      specialistCellTypes = setOf(SpecialistCellType.SAFE_CELL),
      maxCapacity = 1,
      workingCapacity = 1,
      userOrSystemInContext = "test",
      clock = clock,
      linkedTransaction = linkedTransaction,
    )

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3ConvertedBack = repository.findOneByPrisonIdAndPathHierarchy(cell3.prisonId, cell3.getPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3ConvertedBack.getDerivedStatus()).isEqualTo(DerivedLocationStatus.ACTIVE)
    assertThat((cell3ConvertedBack as Cell).accommodationType).isEqualTo(AccommodationType.NORMAL_ACCOMMODATION)
    assertThat((cell3ConvertedBack).getMaxCapacity()).isEqualTo(1)
    assertThat(cell3ConvertedBack.isCertified()).isTrue()
  }

  private fun buildResLocation(
    pathHierarchy: String,
    prisonId: String = "MDI",
    locationType: LocationType = LocationType.CELL,
    status: LocationStatus = LocationStatus.ACTIVE,
    parent: Location? = null,
  ): ResidentialLocation {
    val now = LocalDateTime.now(clock)
    return ResidentialLocation(
      code = pathHierarchy.split("-").last(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      locationType = locationType,
      status = status,
      createdBy = SYSTEM_USERNAME,
      whenCreated = now,
      parent = parent,
      localName = "$locationType $prisonId $pathHierarchy",
      deactivatedDate = LocalDateTime.now(clock).minusYears(1),
      proposedReactivationDate = LocalDate.now(clock).minusDays(1),
      orderWithinParentLocation = 1,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "comments",
      childLocations = mutableListOf(),
    )
  }

  private fun buildCell(
    pathHierarchy: String,
    prisonId: String = "MDI",
    status: LocationStatus = LocationStatus.ACTIVE,
    parent: Location? = null,
    residentialAttributeValues: Set<ResidentialAttributeValue>,
  ): Location {
    val now = LocalDateTime.now(clock)
    val location = Cell(
      code = pathHierarchy.split("-").last(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      locationType = LocationType.CELL,
      status = status,
      createdBy = SYSTEM_USERNAME,
      whenCreated = now,
      parent = parent,
      capacity = Capacity(maxCapacity = 1, workingCapacity = 1),
      certification = Certification(certified = true, capacityOfCertifiedCell = 1),
      localName = "CELL $prisonId $pathHierarchy",
      deactivatedDate = LocalDateTime.now(clock).minusYears(1),
      proposedReactivationDate = LocalDate.now(clock).minusDays(1),
      orderWithinParentLocation = 1,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "comments",
      childLocations = mutableListOf(),
    )
    location.addAttributes(residentialAttributeValues)
    return location
  }

  private fun buildNonResLocation(
    pathHierarchy: String,
    prisonId: String = "MDI",
    locationType: LocationType,
    status: LocationStatus = LocationStatus.ACTIVE,
    parent: Location? = null,
  ): Location {
    val now = LocalDateTime.now(clock)
    val location = NonResidentialLocation(
      code = pathHierarchy.split("-").last(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      locationType = locationType,
      status = status,
      createdBy = SYSTEM_USERNAME,
      whenCreated = now,
      parent = parent,
      localName = "$locationType $prisonId $pathHierarchy",
      orderWithinParentLocation = 1,
      comments = "Non Res comments",
      childLocations = mutableListOf(),
    )
    location.addUsage(NonResidentialUsageType.ADJUDICATION_HEARING)
    return location
  }
}
