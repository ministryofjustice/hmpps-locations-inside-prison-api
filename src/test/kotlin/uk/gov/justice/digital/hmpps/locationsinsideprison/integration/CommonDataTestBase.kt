package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildVirtualResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDateTime

const val EXPECTED_USERNAME = "A_TEST_USER"

@WithMockAuthUser(username = EXPECTED_USERNAME)
class CommonDataTestBase : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: LocationRepository

  @Autowired
  lateinit var linkedTransactionRepository: LinkedTransactionRepository
  lateinit var cell1: Cell
  lateinit var cell2: Cell
  lateinit var cell1N: Cell
  lateinit var inactiveCellB3001: Cell
  lateinit var archivedCell: Cell
  lateinit var landingZ1: ResidentialLocation
  lateinit var landingZ2: ResidentialLocation
  lateinit var landingB3: ResidentialLocation
  lateinit var landingN1: ResidentialLocation
  lateinit var wingZ: ResidentialLocation
  lateinit var wingB: ResidentialLocation
  lateinit var wingN: ResidentialLocation
  lateinit var visitRoom: NonResidentialLocation
  lateinit var adjRoom: NonResidentialLocation
  lateinit var store: ResidentialLocation
  lateinit var cswap: VirtualResidentialLocation
  lateinit var tap: VirtualResidentialLocation
  lateinit var linkedTransaction: LinkedTransaction

  @BeforeEach
  fun setUp() {
    prisonerSearchMockServer.resetAll()
    prisonRegisterMockServer.resetAll()
    repository.deleteAll()

    linkedTransaction = linkedTransactionRepository.saveAndFlush(
      LinkedTransaction(
        transactionType = TransactionType.LOCATION_CREATE,
        transactionDetail = "Initial Data Load",
        transactionInvokedBy = EXPECTED_USERNAME,
        txStartTime = LocalDateTime.now(clock),
      ),
    )

    wingN = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A",
        locationType = LocationType.WING,
        localName = "WING A",
      ),
    )
    cell1N = repository.save(
      buildCell(
        pathHierarchy = "A-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        prisonId = "NMI",
        residentialHousingType = ResidentialHousingType.OTHER_USE,
        linkedTransaction = linkedTransaction,
      ),
    )
    landingN1 = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A-1",
        locationType = LocationType.LANDING,
        localName = "LANDING A",
      ),
    )

    wingN.addChildLocation(landingN1.addChildLocation(cell1N))
    repository.save(wingN)

    wingZ = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z",
        locationType = LocationType.WING,
      ),
    )
    wingB = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B",
        localName = "Wing B",
        locationType = LocationType.WING,
      ),
    )
    landingZ1 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-1",
        localName = "Landing 1",
        locationType = LocationType.LANDING,
      ),
    )
    landingZ2 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-2",
        localName = "Landing 2",
        locationType = LocationType.LANDING,
      ),
    )
    landingB3 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B-A",
        localName = "Landing 1",
        locationType = LocationType.LANDING,
      ),
    )
    cell1 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        linkedTransaction = linkedTransaction,
      ),
    )
    cell2 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-002",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        residentialAttributeValues = setOf(
          ResidentialAttributeValue.CAT_A,
          ResidentialAttributeValue.SAFE_CELL,
          ResidentialAttributeValue.DOUBLE_OCCUPANCY,
        ),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
        accommodationType = AccommodationType.CARE_AND_SEPARATION,
        linkedTransaction = linkedTransaction,
      ),
    )
    store = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-1-01S",
        locationType = LocationType.STORE,
        localName = "Store Room",
        residentialHousingType = ResidentialHousingType.OTHER_USE,
      ),
    )
    inactiveCellB3001 = repository.save(
      buildCell(
        pathHierarchy = "B-A-001",
        active = false,
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
        linkedTransaction = linkedTransaction,
      ),
    )

    archivedCell = repository.save(
      buildCell(
        pathHierarchy = "Z-1-003",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        active = false,
        archived = true,
        linkedTransaction = linkedTransaction,
      ),
    )

    visitRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "VISIT",
        locationType = LocationType.VISITS,
        nonResidentialUsageType = NonResidentialUsageType.VISIT,
      ),
    )
    adjRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "ADJUDICATION",
        locationType = LocationType.ADJUDICATION_ROOM,
        nonResidentialUsageType = NonResidentialUsageType.ADJUDICATION_HEARING,
      ),
    )
    wingZ.addChildLocation(visitRoom)
      .addChildLocation(adjRoom)
      .addChildLocation(
        landingZ1
          .addChildLocation(cell1)
          .addChildLocation(cell2)
          .addChildLocation(archivedCell)
          .addChildLocation(store),
      )
      .addChildLocation(landingZ2)

    wingZ.updateComments(
      "A New Comment",
      EXPECTED_USERNAME,
      clock,
      linkedTransaction,
    )

    wingB.addChildLocation(landingB3.addChildLocation(inactiveCellB3001))
    repository.save(wingZ)
    repository.save(wingB)

    tap = repository.save(
      buildVirtualResidentialLocation(
        pathHierarchy = "TAP",
        localName = "Temp Absentee Prisoner",
        capacity = Capacity(maxCapacity = 99, workingCapacity = 0),
      ),
    )

    cswap = repository.save(
      buildVirtualResidentialLocation(
        pathHierarchy = "CSWAP",
        localName = "Cell Swap",
        capacity = Capacity(maxCapacity = 99, workingCapacity = 0),
      ),
    )
  }
}
