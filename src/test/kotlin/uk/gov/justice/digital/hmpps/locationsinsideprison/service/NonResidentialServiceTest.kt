package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeHiddenFromListException
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
    whenever(linkedTransactionRepository.save(any<LinkedTransaction>())).thenReturn(mock())
    whenever(nonResidentialLocationRepository.save(any<NonResidentialLocation>())).thenAnswer {
      val loc = it.arguments[0] as NonResidentialLocation
      if (loc.id == null) {
        ReflectionTestUtils.setField(loc, "id", UUID.randomUUID())
      }
      loc
    }
  }

  // findAllByPrisonIdWithNonResidentialUsages
  @Test
  fun `should format local name`() {
    val prisonLocation = buildLocation("BULLINGDON (HMP)")
    whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(any())).thenReturn(
      listOf(prisonLocation),
    )

    val nonResLoc =
      service.getByPrisonWithUsageTypes(
        "prisonId",
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

    whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonWithUsageTypes(
        "prisonId",
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

    whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonWithUsageTypes("prisonId")
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("CC")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("B")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("A")
  }

  @Test
  fun `should sort by localName and format localName`() {
    val locations = listOf(location3, location2, location1)

    whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(any())).thenReturn(
      locations,
    )

    val nonResLoc =
      service.getByPrisonWithUsageTypes(
        "prisonId",
        sortByLocalName = true,
        formatLocalName = true,
      )
    Assertions.assertThat(nonResLoc[0].localName).isEqualTo("A")
    Assertions.assertThat(nonResLoc[1].localName).isEqualTo("B")
    Assertions.assertThat(nonResLoc[2].localName).isEqualTo("Cc")
  }

  @Nested
  inner class AlignChildrenToParentName {
    private val prisonId = "prisonId"

    private fun givenParents(vararg parents: NonResidentialLocation, otherLocations: List<NonResidentialLocation> = emptyList()) {
      whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialServices(prisonId)).thenReturn(parents.toList())
      val everyLocation = parents.flatMap { listOf(it) + it.findSubLocations().filterIsInstance<NonResidentialLocation>() } + otherLocations
      whenever(nonResidentialLocationRepository.findAllByPrisonId(prisonId)).thenReturn(everyLocation)
      whenever(sharedLocationService.getUsername()).thenReturn("test-user")
      whenever(sharedLocationService.createLinkedTransaction(any(), any(), any(), anyOrNull())).thenReturn(mock())
    }

    private fun gymWithChildren(vararg children: NonResidentialLocation) = buildLocation("Gym", code = "GYM").apply {
      addService(ServiceType.APPOINTMENT)
      addService(ServiceType.PROGRAMMES_AND_ACTIVITIES)
      children.forEach { addChildLocation(it) }
    }

    @Test
    fun `dry run reports a child to create and changes nothing`() {
      givenParents(gymWithChildren(buildLocation("Gym Area 1", code = "A1")))

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = true))

      val parentResult = result.report.parents.single()
      Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.CREATE_CHILD)
      Assertions.assertThat(parentResult.createdChild?.name).isEqualTo("Gym")
      Assertions.assertThat(parentResult.createdChild?.id).isNull()
      Assertions.assertThat(result.created).isEmpty()
      Assertions.assertThat(result.renamed).isEmpty()
      verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
    }

    @Test
    fun `creates a child with the parent name and all of its services`() {
      givenParents(gymWithChildren(buildLocation("Gym Area 1", code = "A1")))

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = false))

      Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.CREATE_CHILD)
      Assertions.assertThat(result.created.single().localName).isEqualTo("Gym")
      val captor = argumentCaptor<NonResidentialLocation>()
      verify(nonResidentialLocationRepository).save(captor.capture())
      Assertions.assertThat(captor.firstValue.services.map { it.serviceType })
        .containsExactlyInAnyOrder(ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES)
    }

    @Test
    fun `leaves a parent alone when exactly one child has its name, ignoring case and spaces`() {
      givenParents(gymWithChildren(buildLocation(" gym ", code = "G1"), buildLocation("Gym Area 1", code = "A1")))

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = false))

      Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.NO_ACTION)
      verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
    }

    @Test
    fun `renames several same-named children with numbers, skipping names already used, then creates one`() {
      val first = buildLocation("Gym", code = "G1")
      val second = buildLocation("GYM", code = "G2")
      givenParents(gymWithChildren(first, second), otherLocations = listOf(buildLocation("Gym 1", code = "OTHER")))

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = false))

      val parentResult = result.report.parents.single()
      Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.RENAME_CHILDREN_AND_CREATE_CHILD)
      Assertions.assertThat(parentResult.renamedChildren.map { it.oldName to it.newName })
        .containsExactly("Gym" to "Gym 2", "GYM" to "Gym 3")
      Assertions.assertThat(first.localName).isEqualTo("Gym 2")
      Assertions.assertThat(second.localName).isEqualTo("Gym 3")
      Assertions.assertThat(result.renamed).hasSize(2)
      Assertions.assertThat(result.created.single().localName).isEqualTo("Gym")
    }

    @Test
    fun `shortens a long parent name so numbered names still fit the local name column`() {
      val longName = "L".repeat(79) + "x"
      val parent = buildLocation(longName, code = "LONG").apply {
        addService(ServiceType.APPOINTMENT)
        addChildLocation(buildLocation(longName, code = "L1"))
        addChildLocation(buildLocation(longName, code = "L2"))
      }
      givenParents(parent)

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = false))

      val newNames = result.report.parents.single().renamedChildren.map { it.newName }
      Assertions.assertThat(newNames).containsExactly("L".repeat(78) + " 1", "L".repeat(78) + " 2")
      Assertions.assertThat(newNames).allSatisfy { Assertions.assertThat(it).hasSizeLessThanOrEqualTo(80) }
      Assertions.assertThat(result.created.single().localName).isEqualTo(longName)
    }

    @Test
    fun `ignores archived children and same-named grandchildren`() {
      val archivedChild = buildLocation("Gym", code = "G1", status = LocationStatus.ARCHIVED)
      val child = buildLocation("Gym Area 1", code = "A1").apply { addChildLocation(buildLocation("Gym", code = "G2")) }
      givenParents(gymWithChildren(archivedChild, child))

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = true))

      Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.CREATE_CHILD)
    }

    @Test
    fun `skips an archived parent`() {
      val parent = gymWithChildren(buildLocation("Gym Area 1", code = "A1")).apply { status = LocationStatus.ARCHIVED }
      givenParents(parent)

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = false))

      Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.SKIPPED)
      Assertions.assertThat(result.report.parents.single().reason).isEqualTo("Parent is archived")
      verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
    }

    @Test
    fun `only considers the parents asked for, and reports ids that are not eligible parents`() {
      val gym = gymWithChildren(buildLocation("Gym Area 1", code = "A1"))
      val chapel = buildLocation("Chapel", code = "CHAPEL").apply {
        addService(ServiceType.APPOINTMENT)
        addChildLocation(buildLocation("Chapel Room", code = "R1"))
      }
      givenParents(gym, chapel)
      val unknownId = UUID.randomUUID()

      val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = true, parentLocationIds = setOf(gym.id!!, unknownId)))

      Assertions.assertThat(result.report.parents.map { it.parentId to it.action })
        .containsExactlyInAnyOrder(gym.id!! to AlignmentAction.CREATE_CHILD, unknownId to AlignmentAction.SKIPPED)
      Assertions.assertThat(result.report.summary[AlignmentAction.CREATE_CHILD]).isEqualTo(1)
    }
  }

  @Test
  fun `hideFromList hides a parent that has no services`() {
    val parent = buildLocation("Parent")
    val child = buildLocation("Child")
    parent.addChildLocation(child)

    whenever(nonResidentialLocationRepository.findById(parent.id!!)).thenReturn(Optional.of(parent))
    whenever(sharedLocationService.getUsername()).thenReturn("test-user")
    whenever(sharedLocationService.createLinkedTransaction(any(), any(), any(), anyOrNull())).thenReturn(mock())

    val result = service.hideFromList(parent.id!!)

    Assertions.assertThat(result.hiddenFromList).isTrue()
    Assertions.assertThat(parent.isHiddenFromList()).isTrue()
    // Not a deactivation
    Assertions.assertThat(parent.status).isEqualTo(LocationStatus.ACTIVE)
  }

  @Test
  fun `hideFromList rejects a leaf location`() {
    val leaf = buildLocation("Leaf")
    whenever(nonResidentialLocationRepository.findById(leaf.id!!)).thenReturn(Optional.of(leaf))

    Assertions.assertThatThrownBy { service.hideFromList(leaf.id!!) }
      .isInstanceOf(LocationCannotBeHiddenFromListException::class.java)
      .hasMessageContaining("not a parent location")
  }

  @Test
  fun `hideFromList rejects a parent still used by a service`() {
    val parent = buildLocation("Parent")
    parent.addChildLocation(buildLocation("Child"))
    parent.addService(ServiceType.APPOINTMENT)
    whenever(nonResidentialLocationRepository.findById(parent.id!!)).thenReturn(Optional.of(parent))

    Assertions.assertThatThrownBy { service.hideFromList(parent.id!!) }
      .isInstanceOf(LocationCannotBeHiddenFromListException::class.java)
      .hasMessageContaining("still used by")
  }

  @Test
  fun `hideFromList rejects a parent that is already hidden`() {
    val parent = buildLocation("Parent")
    parent.addChildLocation(buildLocation("Child"))
    parent.hideFromList("someone", clock, mock())
    whenever(nonResidentialLocationRepository.findById(parent.id!!)).thenReturn(Optional.of(parent))

    Assertions.assertThatThrownBy { service.hideFromList(parent.id!!) }
      .isInstanceOf(LocationCannotBeHiddenFromListException::class.java)
      .hasMessageContaining("already hidden")
  }

  private fun buildLocation(
    localName: String,
    code: String = "code",
    status: LocationStatus = LocationStatus.ACTIVE,
  ): NonResidentialLocation = NonResidentialLocation(
    id = UUID.randomUUID(),
    localName = localName,
    code = code,
    pathHierarchy = if (code == "code") "path-a" else code,
    locationType = LocationType.LOCATION,
    prisonId = "prisonId",
    status = status,
    whenCreated = LocalDateTime.now(),
    childLocations = sortedSetOf(),
    createdBy = "createdBy",
  )
}
