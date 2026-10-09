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

    private fun givenParents(vararg parents: NonResidentialLocation) {
      whenever(nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialServices(prisonId)).thenReturn(parents.toList())
      whenever(sharedLocationService.getUsername()).thenReturn("test-user")
      whenever(sharedLocationService.createLinkedTransaction(any(), any(), any(), anyOrNull())).thenReturn(mock())
    }

    private fun gymWithChildren(vararg children: NonResidentialLocation) = buildLocation("Gym", code = "GYM").apply {
      addService(ServiceType.APPOINTMENT)
      addService(ServiceType.PROGRAMMES_AND_ACTIVITIES)
      children.forEach { addChildLocation(it) }
    }

    private fun child(name: String, code: String, vararg services: ServiceType) = buildLocation(name, code = code).apply {
      services.forEach { addService(it) }
    }

    private fun align(step: AlignmentStep, dryRun: Boolean) = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(step = step, dryRun = dryRun))

    @Nested
    inner class Step1Align {

      @Test
      fun `dry run reports a child to create and changes nothing`() {
        givenParents(gymWithChildren(child("Gym Area 1", "A1")))

        val result = align(AlignmentStep.ALIGN, dryRun = true)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.CREATE_CHILD)
        Assertions.assertThat(parentResult.createdChild?.name).isEqualTo("Gym")
        Assertions.assertThat(parentResult.createdChild?.id).isNull()
        Assertions.assertThat(result.created).isEmpty()
        Assertions.assertThat(result.amended).isEmpty()
        verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
      }

      @Test
      fun `creates a child with the parent name and all of its services`() {
        givenParents(gymWithChildren(child("Gym Area 1", "A1")))

        val result = align(AlignmentStep.ALIGN, dryRun = false)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.CREATE_CHILD)
        Assertions.assertThat(result.created.single().localName).isEqualTo("Gym")
        val captor = argumentCaptor<NonResidentialLocation>()
        verify(nonResidentialLocationRepository).save(captor.capture())
        Assertions.assertThat(captor.firstValue.services.map { it.serviceType })
          .containsExactlyInAnyOrder(ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES)
      }

      @Test
      fun `leaves a parent alone when one child has its name and all its services, ignoring case and spaces`() {
        givenParents(gymWithChildren(child(" gym ", "G1", ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES), child("Gym Area 1", "A1")))

        val result = align(AlignmentStep.ALIGN, dryRun = false)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.NO_ACTION)
        Assertions.assertThat(result.amended).isEmpty()
        verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
      }

      @Test
      fun `adds the parent's missing services to the one child with its name instead of creating another`() {
        val sameNamedChild = child("Gym", "G1", ServiceType.USE_OF_FORCE)
        givenParents(gymWithChildren(sameNamedChild, child("Gym Area 1", "A1")))

        val result = align(AlignmentStep.ALIGN, dryRun = false)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.ADD_SERVICES_TO_CHILD)
        Assertions.assertThat(parentResult.keptChild?.id).isEqualTo(sameNamedChild.id)
        Assertions.assertThat(parentResult.servicesAddedToChild)
          .containsExactlyInAnyOrder(ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES)
        Assertions.assertThat(sameNamedChild.services.map { it.serviceType })
          .containsExactlyInAnyOrder(ServiceType.USE_OF_FORCE, ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES)
        Assertions.assertThat(result.amended.single().id).isEqualTo(sameNamedChild.id)
        Assertions.assertThat(result.created).isEmpty()
      }

      @Test
      fun `dry run reports the services it would add and changes nothing`() {
        val sameNamedChild = child("Gym", "G1", ServiceType.USE_OF_FORCE)
        givenParents(gymWithChildren(sameNamedChild))

        val result = align(AlignmentStep.ALIGN, dryRun = true)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.ADD_SERVICES_TO_CHILD)
        Assertions.assertThat(sameNamedChild.services.map { it.serviceType }).containsExactly(ServiceType.USE_OF_FORCE)
        Assertions.assertThat(result.amended).isEmpty()
      }

      @Test
      fun `with several same-named children, keeps one used by Activities or Appointments and lists the rest without renaming or archiving them`() {
        val otherService = child("Gym", "G1", ServiceType.USE_OF_FORCE)
        val usedByAppointments = child("GYM", "G2", ServiceType.APPOINTMENT)
        val noServices = child("gym", "G3")
        givenParents(gymWithChildren(otherService, usedByAppointments, noServices))

        val result = align(AlignmentStep.ALIGN, dryRun = false)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.ADD_SERVICES_TO_CHILD)
        Assertions.assertThat(parentResult.keptChild?.id).isEqualTo(usedByAppointments.id)
        Assertions.assertThat(parentResult.servicesAddedToChild).containsExactly(ServiceType.PROGRAMMES_AND_ACTIVITIES)
        Assertions.assertThat(parentResult.duplicateChildren.map { Triple(it.id!!, it.canBeArchived, it.reasonCannotArchive) }).containsExactlyInAnyOrder(
          Triple(otherService.id!!, false, "Used by other services: USE_OF_FORCE"),
          Triple(noServices.id!!, true, null),
        )
        Assertions.assertThat(parentResult.duplicateChildren).allSatisfy { Assertions.assertThat(it.archived).isFalse() }
        Assertions.assertThat(result.report.duplicatesThatCanBeArchived).isEqualTo(1)
        Assertions.assertThat(result.report.duplicatesThatCannotBeArchived).isEqualTo(1)
        Assertions.assertThat(listOf(otherService, usedByAppointments, noServices).map { it.localName }).containsExactly("Gym", "GYM", "gym")
        Assertions.assertThat(listOf(otherService, noServices).map { it.status }).containsOnly(LocationStatus.ACTIVE)
        Assertions.assertThat(result.created).isEmpty()
      }

      @Test
      fun `when no same-named child is used by Activities or Appointments, keeps the first by code`() {
        val first = child("Gym", "G1")
        val second = child("Gym", "G2")
        givenParents(gymWithChildren(second, first))

        val result = align(AlignmentStep.ALIGN, dryRun = true)

        Assertions.assertThat(result.report.parents.single().keptChild?.id).isEqualTo(first.id)
      }

      @Test
      fun `ignores archived children and same-named grandchildren`() {
        val archivedChild = buildLocation("Gym", code = "G1", status = LocationStatus.ARCHIVED)
        val areaWithSameNamedChild = child("Gym Area 1", "A1").apply { addChildLocation(child("Gym", "G2")) }
        givenParents(gymWithChildren(archivedChild, areaWithSameNamedChild))

        val result = align(AlignmentStep.ALIGN, dryRun = true)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.CREATE_CHILD)
      }

      @Test
      fun `skips an archived parent`() {
        val parent = gymWithChildren(child("Gym Area 1", "A1")).apply { status = LocationStatus.ARCHIVED }
        givenParents(parent)

        val result = align(AlignmentStep.ALIGN, dryRun = false)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.SKIPPED)
        Assertions.assertThat(result.report.parents.single().reason).isEqualTo("Parent is archived")
        verify(nonResidentialLocationRepository, never()).save(any<NonResidentialLocation>())
      }

      @Test
      fun `only considers the parents asked for, and reports ids that are not eligible parents`() {
        val gym = gymWithChildren(child("Gym Area 1", "A1"))
        val chapel = buildLocation("Chapel", code = "CHAPEL").apply {
          addService(ServiceType.APPOINTMENT)
          addChildLocation(child("Chapel Room", "R1"))
        }
        givenParents(gym, chapel)
        val unknownId = UUID.randomUUID()

        val result = service.alignChildrenToParentName(prisonId, AlignChildrenToParentNameRequest(dryRun = true, parentLocationIds = setOf(gym.id!!, unknownId)))

        Assertions.assertThat(result.report.parents.map { it.parentId to it.action })
          .containsExactlyInAnyOrder(gym.id!! to AlignmentAction.CREATE_CHILD, unknownId to AlignmentAction.SKIPPED)
        Assertions.assertThat(result.report.step).isEqualTo(AlignmentStep.ALIGN)
        Assertions.assertThat(result.report.summary[AlignmentAction.CREATE_CHILD]).isEqualTo(1)
      }
    }

    @Nested
    inner class Step2ArchiveDuplicates {

      @Test
      fun `archives duplicates used only by Activities or Appointments, and reports those it cannot archive`() {
        val kept = child("Gym", "G1", ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES)
        val appointmentsOnly = child("Gym", "G2", ServiceType.APPOINTMENT)
        val noServices = child("Gym", "G3")
        val otherService = child("Gym", "G4", ServiceType.INTERNAL_MOVEMENTS)
        val hasChildren = child("Gym", "G5").apply { addChildLocation(child("Store", "S1")) }
        givenParents(gymWithChildren(kept, appointmentsOnly, noServices, otherService, hasChildren))

        val result = align(AlignmentStep.ARCHIVE_DUPLICATES, dryRun = false)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.ARCHIVE_SOME_DUPLICATES)
        Assertions.assertThat(parentResult.keptChild?.id).isEqualTo(kept.id)
        Assertions.assertThat(parentResult.duplicateChildren.map { Triple(it.id!!, it.archived, it.reasonCannotArchive) }).containsExactlyInAnyOrder(
          Triple(appointmentsOnly.id!!, true, null),
          Triple(noServices.id!!, true, null),
          Triple(otherService.id!!, false, "Used by other services: INTERNAL_MOVEMENTS"),
          Triple(hasChildren.id!!, false, "Has child locations of its own"),
        )
        Assertions.assertThat(listOf(appointmentsOnly, noServices).map { it.status }).containsOnly(LocationStatus.ARCHIVED)
        Assertions.assertThat(listOf(kept, otherService, hasChildren).map { it.status }).containsOnly(LocationStatus.ACTIVE)
        Assertions.assertThat(result.amended.map { it.id }).containsExactlyInAnyOrder(appointmentsOnly.id, noServices.id)
        Assertions.assertThat(result.created).isEmpty()
      }

      @Test
      fun `dry run reports what it would archive and changes nothing`() {
        val duplicate = child("Gym", "G2")
        givenParents(gymWithChildren(child("Gym", "G1", ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES), duplicate))

        val result = align(AlignmentStep.ARCHIVE_DUPLICATES, dryRun = true)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.ARCHIVE_DUPLICATES)
        Assertions.assertThat(parentResult.duplicateChildren.single().canBeArchived).isTrue()
        Assertions.assertThat(parentResult.duplicateChildren.single().archived).isFalse()
        Assertions.assertThat(duplicate.status).isEqualTo(LocationStatus.ACTIVE)
        Assertions.assertThat(result.amended).isEmpty()
      }

      @Test
      fun `reports a parent whose duplicates cannot be archived`() {
        val duplicate = child("Gym", "G2", ServiceType.USE_OF_FORCE)
        givenParents(gymWithChildren(child("Gym", "G1", ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES), duplicate))

        val result = align(AlignmentStep.ARCHIVE_DUPLICATES, dryRun = false)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.CANNOT_ARCHIVE_DUPLICATES)
        Assertions.assertThat(duplicate.status).isEqualTo(LocationStatus.ACTIVE)
        Assertions.assertThat(result.amended).isEmpty()
      }

      @Test
      fun `skips a parent until step 1 has given the kept child all of the parent's services`() {
        val duplicate = child("Gym", "G2")
        givenParents(gymWithChildren(child("Gym", "G1", ServiceType.APPOINTMENT), duplicate))

        val result = align(AlignmentStep.ARCHIVE_DUPLICATES, dryRun = false)

        val parentResult = result.report.parents.single()
        Assertions.assertThat(parentResult.action).isEqualTo(AlignmentAction.SKIPPED)
        Assertions.assertThat(parentResult.reason).startsWith("Run step 1 first")
        Assertions.assertThat(duplicate.status).isEqualTo(LocationStatus.ACTIVE)
      }

      @Test
      fun `does nothing where there are no duplicates`() {
        givenParents(gymWithChildren(child("Gym", "G1", ServiceType.APPOINTMENT, ServiceType.PROGRAMMES_AND_ACTIVITIES), child("Gym Area 1", "A1")))

        val result = align(AlignmentStep.ARCHIVE_DUPLICATES, dryRun = false)

        Assertions.assertThat(result.report.parents.single().action).isEqualTo(AlignmentAction.NO_ACTION)
        Assertions.assertThat(result.amended).isEmpty()
      }
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
