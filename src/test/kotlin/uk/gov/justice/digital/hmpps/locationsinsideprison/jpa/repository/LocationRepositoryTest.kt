package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class LocationRepositoryTest : TestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  fun findCellsOnAWingTest() {
    val wing = buildResLocation("A", locationType = LocationType.WING)
    val landing1 = buildResLocation("A-1", locationType = LocationType.LANDING)
    val landing2 = buildResLocation("A-2", locationType = LocationType.LANDING)
    val cell001L1 = buildResLocation("A-1-001", locationType = LocationType.CELL)
    val cell002L1 = buildResLocation("A-1-002", locationType = LocationType.CELL)
    val cell002L2 = buildResLocation("A-2-001", locationType = LocationType.CELL)
    val adjRoom = buildNonResLocation("A-ADJ", locationType = LocationType.ADJUDICATION_ROOM, active = true)
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
      if (it is ResidentialLocation) {
        assertThat(it.capacity?.operationalCapacity).isEqualTo(1)
        assertThat(it.certification?.capacityOfCertifiedCell).isEqualTo(1)
      }
    }

    assertThat(location.findAllLeafLocations()).containsExactlyInAnyOrder(cell001L1, cell002L1, cell002L2, adjRoom)
    location.findAllLeafLocations().forEach {
      if (it is ResidentialLocation) {
        it.capacity?.operationalCapacity = 2
        it.certification?.capacityOfCertifiedCell = 2
      }
    }

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell2 = repository.findOneByPrisonIdAndPathHierarchy(cell002L2.prisonId, cell002L2.getLocationPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell2.findTopLevelLocation()).isEqualTo(wing)
    assertThat(cell2.getLocationPathHierarchy()).isEqualTo("A-2-001")
    cell2 as ResidentialLocation
    assertThat(cell2.capacity?.operationalCapacity).isEqualTo(2)
    assertThat(cell2.certification?.capacityOfCertifiedCell).isEqualTo(2)

    val landing1Retrieved = repository.findOneByPrisonIdAndPathHierarchy(landing1.prisonId, landing1.getLocationPathHierarchy()) ?: throw Exception("Location not found")
    cell2.setCode("003")
    cell2.setParent(landing1Retrieved)

    repository.save(landing1Retrieved)
    repository.save(cell2)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3 = repository.findOneByPrisonIdAndPathHierarchy(cell2.prisonId, cell2.getLocationPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3.findTopLevelLocation()).isEqualTo(wing)
    assertThat(cell3.getParent()?.getCode()).isEqualTo(landing1.getCode())

    cell3.getParent()?.getParent()?.setCode("T")
    repository.save(cell3)

    TestTransaction.flagForCommit()
    TestTransaction.end()
    TestTransaction.start()

    val cell3Renamed = repository.findOneByPrisonIdAndPathHierarchy(cell3.prisonId, cell3.getLocationPathHierarchy()) ?: throw Exception("Location not found")
    assertThat(cell3Renamed.getLocationPathHierarchy()).isEqualTo("T-1-003")
  }

  private fun buildResLocation(
    pathHierarchy: String,
    prisonId: String = "MDI",
    locationType: LocationType = LocationType.CELL,
    active: Boolean = true,
    parent: Location? = null,
  ): Location {
    val now = LocalDateTime.now(clock)
    val location = ResidentialLocation(
      code = pathHierarchy.split("-").last(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      locationType = locationType,
      active = active,
      updatedBy = SYSTEM_USERNAME,
      whenUpdated = now,
      whenCreated = now,
      parent = parent,
      capacity = if (locationType == LocationType.CELL) {
        Capacity(capacity = 1, operationalCapacity = 1)
      } else {
        null
      },
      certification = if (locationType == LocationType.CELL) {
        Certification(certified = true, capacityOfCertifiedCell = 1)
      } else {
        null
      },
      description = "$locationType $prisonId $pathHierarchy",
      deactivatedDate = LocalDate.now(clock).minusYears(1),
      reactivatedDate = LocalDate.now(clock).minusDays(1),
      orderWithinParentLocation = 1,
      residentialHousingType = ResidentialHousingType.NORMAL_ACCOMMODATION,
      comments = "comments",
      childLocations = mutableListOf(),
      deactivatedReason = null,
      id = null,
    )
    location.addAttribute(ResidentialAttributeValue.UF_1)
    return location
  }

  private fun buildNonResLocation(
    pathHierarchy: String,
    prisonId: String = "MDI",
    locationType: LocationType = LocationType.CELL,
    active: Boolean = true,
    parent: Location? = null,
  ): Location {
    val now = LocalDateTime.now(clock)
    val location = NonResidentialLocation(
      code = pathHierarchy.split("-").last(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      locationType = locationType,
      active = active,
      updatedBy = SYSTEM_USERNAME,
      whenUpdated = now,
      whenCreated = now,
      parent = parent,
      description = "$locationType $prisonId $pathHierarchy",
      deactivatedDate = null,
      reactivatedDate = null,
      orderWithinParentLocation = 1,
      comments = "Non Res comments",
      childLocations = mutableListOf(),
      deactivatedReason = null,
      id = null,
    )
    location.addUsage(NonResidentialUsageType.ADJUDICATION_HEARING)
    return location
  }
}
