package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ValidationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateOrUpdateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreatePropertyLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PropertyLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdatePropertyLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceFamilyType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.excludeByCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.excludePropertyOnlyLocations
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByIsLeaf
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByLocalName
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByPrisonId
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByServiceTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByStatusesTreatingHiddenAsArchived
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DuplicateNonResidentialLocalNameInPrisonException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeHiddenFromListException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService.Companion.log
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.isNotEmpty
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.pow
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Service
@Transactional(readOnly = true)
class NonResidentialService(
  private val locationRepository: LocationRepository,
  private val nonResidentialLocationRepository: NonResidentialLocationRepository,
  private val commonLocationService: SharedLocationService,
  private val clock: Clock,
) {

  fun getById(id: UUID): NonResidentialLocationDTO? = nonResidentialLocationRepository.findById(id).getOrNull()?.toNonResidentialDto()

  /**
   * Resolve several non-residential locations by id in one call. The repository only returns
   * non-residential entities, so ids that are residential or unknown are simply absent from the result.
   */
  fun getByIds(ids: List<UUID>): List<NonResidentialLocationDTO> = nonResidentialLocationRepository.findAllById(ids)
    .map { it.toNonResidentialDto() }
    .sortedBy { it.pathHierarchy }

  fun getByPrisonAndServiceType(
    prisonId: String,
    serviceType: ServiceType? = null,
    sortByLocalName: Boolean = false,
    formatLocalName: Boolean = false,
    filterParents: Boolean = true,
  ): List<LocationDTO> {
    val filteredByUsage = serviceType?.let {
      nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialService(prisonId, serviceType)
    } ?: nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialServices(prisonId)

    val filteredResults = filteredByUsage
      .filter { it.isActiveAndAllParentsActive() }
      .filter { !filterParents || it.findSubLocations().intersect(filteredByUsage.toSet()).isEmpty() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      filteredResults.sortedBy { it.localName }
    } else {
      filteredResults
    }
  }

  fun getActiveNonResidentialLocationsForPrison(
    prisonId: String,
    sortByLocalName: Boolean = true,
    formatLocalName: Boolean = true,
  ): List<LocationDTO> {
    val results = nonResidentialLocationRepository.findAllByPrisonId(prisonId)
      .filter { it.isActiveAndAllParentsActive() }
      .filter { it.getLocationCode() != "RTU" }
      .map { it.toDto(formatLocalName = formatLocalName) }
    return if (sortByLocalName) {
      results.sortedBy { it.localName }
    } else {
      results.sortedBy { it.getKey() }
    }
  }

  fun findByPrisonIdAndLocalName(prisonId: String, localName: String): List<LocationDTO> {
    val locations = nonResidentialLocationRepository.findAllByPrisonIdAndLocalName(prisonId, localName)
    val activeLocations = locations
      .filter { !it.isPermanentlyDeactivated() }
      .map { it.toDto() }

    if (activeLocations.isEmpty()) {
      val errorMessage = "No location found with prisonId $prisonId and localName $localName"
      throw LocationNotFoundException(errorMessage)
    }
    return activeLocations
  }

  fun getByPrisonWithUsageTypes(
    prisonId: String,
    sortByLocalName: Boolean = false,
    formatLocalName: Boolean = false,
    filterParents: Boolean = true,
  ): List<LocationDTO> {
    val filteredByUsage = nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(prisonId)

    val filteredResults = filteredByUsage
      .filter { it.getLocationCode() != "RTU" }
      .filter { it.isActiveAndAllParentsActive() }
      .filter { !filterParents || it.findSubLocations().intersect(filteredByUsage.toSet()).isEmpty() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      filteredResults.sortedBy { it.localName }
    } else {
      filteredResults
    }
  }

  /**
   * The leaf locations in a prison that can hold property, each with its PROPERTY-usage capacity.
   * A location can hold property iff it has a non-residential usage of type PROPERTY (any location type).
   * Parents are dropped when a sub-location is also a property location (leaf-only), so capacity is not
   * double-counted; inactive locations and the RTU code are excluded.
   */
  fun getPropertyLocations(prisonId: String): List<PropertyLocationDto> {
    val propertyLocations = nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(prisonId, NonResidentialUsageType.PROPERTY)
    return propertyLocations
      .filter { it.getLocationCode() != "RTU" }
      .filter { it.isActiveAndAllParentsActive() }
      .filter { it.findSubLocations().intersect(propertyLocations.toSet()).isEmpty() }
      .map { it.toPropertyLocationDto() }
      .sortedBy { it.localName ?: it.pathHierarchy }
  }

  /**
   * A single property storage location by id, with its PROPERTY-usage capacity, or null if the id is
   * unknown or the location cannot hold property (no PROPERTY non-residential usage). Callers use this to
   * confirm a location can store property without needing to know how that is modelled here.
   */
  fun getPropertyLocation(id: UUID): PropertyLocationDto? {
    val location = nonResidentialLocationRepository.findById(id).getOrNull() ?: return null
    if (NonResidentialUsageType.PROPERTY !in location.toUsageTypes()) return null
    return location.toPropertyLocationDto()
  }

  /**
   * Make a property storage location available in a prison, either by reinstating one or by creating a new one.
   *
   * Removing a property location only drops its PROPERTY usage - the location itself lives on - so a user who
   * removes one and then adds it back by the same name is really asking for the original location again, not a
   * second record. When such a location exists (see [NonResidentialLocation.canBeReinstatedAsPropertyLocation])
   * its designation is put back with the newly requested capacity, keeping its id, code and history; otherwise
   * a new top-level BOX location is created with a generated code. Reinstating also avoids the code drift a
   * duplicate would suffer, since [generateUniqueNonResidentialCode] lengthens the code when the natural one
   * is taken.
   *
   * [PropertyLocationWriteResult.reinstated] tells the caller which happened, so it can report the right
   * status and raise an amended rather than a created event - NOMIS already knows about a reinstated location.
   */
  @Transactional
  fun createPropertyLocation(
    prisonId: String,
    request: CreatePropertyLocationRequest,
  ): PropertyLocationWriteResult {
    findReinstatablePropertyLocation(prisonId, request.localName)
      ?.let { return reinstatePropertyLocation(it, request) }

    validateLocalNameNotDuplicated(prisonId, request.localName)

    val code = generateUniqueNonResidentialCode(prisonId, request.localName)
    val username = commonLocationService.getUsername()

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.LOCATION_CREATE_NON_RESI,
      "Create property location $code in prison $prisonId",
    )

    val locationToCreate = NonResidentialLocation(
      code = code,
      pathHierarchy = code,
      locationType = LocationType.BOX,
      prisonId = prisonId,
      status = LocationStatus.ACTIVE,
      localName = request.localName,
      childLocations = sortedSetOf(),
      whenCreated = LocalDateTime.now(clock),
      createdBy = username,
    ).apply {
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = username,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
      setPropertyCapacity(request.capacity, username, clock, linkedTransaction)
    }

    val created = nonResidentialLocationRepository.save(locationToCreate)
    log.info("Property location ${created.id} created in $prisonId")
    commonLocationService.trackLocationUpdate(created, "Created Property Location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)

    return PropertyLocationWriteResult(created.toPropertyLocationDto(), created.toNonResidentialDto(), reinstated = false)
  }

  /**
   * The location in [prisonId] named [localName] whose property designation was removed and can be put back,
   * or null when there is none. The name match is case-insensitive (in the query), and where more than one
   * candidate somehow shares the name the oldest is chosen so the outcome is deterministic.
   */
  private fun findReinstatablePropertyLocation(prisonId: String, localName: String): NonResidentialLocation? = nonResidentialLocationRepository
    .findAllByPrisonIdAndLocalName(prisonId = prisonId, localName = localName)
    .asSequence()
    .filter { it.canBeReinstatedAsPropertyLocation() }
    .minWithOrNull(compareBy<NonResidentialLocation> { it.whenCreated }.thenBy { it.getKey() })

  /** Put the property designation back on [location], with the capacity from [request]. */
  private fun reinstatePropertyLocation(
    location: NonResidentialLocation,
    request: CreatePropertyLocationRequest,
  ): PropertyLocationWriteResult {
    val username = commonLocationService.getUsername()
    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Reinstate property designation on ${location.getKey()}",
    )

    // Records the usage coming back (and any capacity change) in history; the location was not created now,
    // so no LOCATION_CREATED history is added.
    location.setPropertyCapacity(request.capacity, username, clock, linkedTransaction)

    log.info("Property location ${location.id} reinstated in ${location.prisonId}")
    commonLocationService.trackLocationUpdate(location, "Reinstated Property Location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)

    return PropertyLocationWriteResult(location.toPropertyLocationDto(), location.toNonResidentialDto(), reinstated = true)
  }

  /**
   * Update a property location's name and/or capacity. Capacity stays on the PROPERTY usage so it
   * continues to sync to NOMIS. Returns the property DTO plus the full non-residential DTO for the event.
   */
  @Transactional
  fun updatePropertyLocation(
    id: UUID,
    request: UpdatePropertyLocationRequest,
  ): Pair<PropertyLocationDto, NonResidentialLocationDTO> {
    val location = findPropertyLocationForUpdate(id)
    val username = commonLocationService.getUsername()

    request.localName?.let { localName ->
      if (localName.lowercase() != location.localName?.lowercase()) {
        validateLocalNameNotDuplicated(location.prisonId, localName, location.id!!)
      }
    }

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Update property location ${location.getKey()}",
    )

    request.localName?.let { location.updateLocalName(it, username, clock, linkedTransaction) }
    request.capacity?.let { location.setPropertyCapacity(it, username, clock, linkedTransaction) }

    commonLocationService.trackLocationUpdate(location, "Updated Property Location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)

    return location.toPropertyLocationDto() to location.toNonResidentialDto()
  }

  /**
   * Remove the PROPERTY designation from a location so it can no longer store property (drops the PROPERTY
   * usage; the location itself is not deleted). Reflected to NOMIS as a removed usage.
   */
  @Transactional
  fun removePropertyLocation(id: UUID): Pair<PropertyLocationDto, NonResidentialLocationDTO> {
    val location = findPropertyLocationForUpdate(id)
    val username = commonLocationService.getUsername()

    if (location.getPropertyCapacity() == null && NonResidentialUsageType.PROPERTY !in location.toUsageTypes()) {
      throw ValidationException("Location ${location.getKey()} is not a property storage location")
    }

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Remove property designation from ${location.getKey()}",
    )

    location.removePropertyUsage(username, clock, linkedTransaction)

    commonLocationService.trackLocationUpdate(location, "Removed Property Location designation")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)

    return location.toPropertyLocationDto() to location.toNonResidentialDto()
  }

  private fun findPropertyLocationForUpdate(id: UUID): NonResidentialLocation {
    val location = nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }
    if (location.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(location.getKey())
    }
    return location
  }

  /**
   * Makes sure each parent non-residential location has exactly one live direct child with the parent's name, so
   * that Activities and Appointments can move bookings from the parent to that child without staff seeing a
   * different location name.
   *
   * Only parents that are not archived, are used by at least one service and have at least one live direct child are
   * considered. Names are compared ignoring case and surrounding spaces. For each parent:
   * - where two or more live direct children share the parent's name, each is renamed with a number ("Gym 1",
   *   "Gym 2"), skipping any name already used in the prison. This runs first, so the step below then applies.
   * - where no live direct child has the parent's name, a child is created with the parent's name, status and all
   *   of its services.
   * - where exactly one live direct child has the parent's name but is not used by all of the parent's services, the
   *   missing services are added to it, so the services can move to that child rather than to a second one of the
   *   same name.
   *
   * With [AlignChildrenToParentNameRequest.dryRun] set, nothing is changed and the report shows what would be done.
   */
  @Transactional
  fun alignChildrenToParentName(prisonId: String, request: AlignChildrenToParentNameRequest): AlignChildrenToParentNameResult {
    val parentsInPrison = nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialServices(prisonId)
      .filter { it.findSubLocations().isNotEmpty() }
    val parentsInScope = request.parentLocationIds?.let { ids -> parentsInPrison.filter { it.id in ids } } ?: parentsInPrison

    val usedNames = nonResidentialLocationRepository.findAllByPrisonId(prisonId)
      .filter { !it.isPermanentlyDeactivated() }
      .mapNotNull { it.localName?.let(::normaliseName) }
      .toMutableSet()

    val results = mutableListOf<ParentAlignmentResult>()
    val created = mutableListOf<LocationDTO>()
    val amended = mutableListOf<LocationDTO>()

    request.parentLocationIds?.filter { id -> parentsInScope.none { it.id == id } }?.forEach { id ->
      results.add(ParentAlignmentResult(parentId = id, action = AlignmentAction.SKIPPED, reason = "Not a parent location used by a service in prison $prisonId"))
    }

    parentsInScope.sortedBy { it.getPathHierarchy() }.forEach { parent ->
      val parentName = parent.localName
      val liveChildren = parent.findSubLocations()
        .filterIsInstance<NonResidentialLocation>()
        .filter { it.getParent()?.id == parent.id && !it.isPermanentlyDeactivated() }
      val skipReason = when {
        parent.isPermanentlyDeactivated() -> "Parent is archived"
        parentName.isNullOrBlank() -> "Parent has no name"
        liveChildren.isEmpty() -> "Parent has no live child locations"
        else -> null
      }
      if (skipReason != null) {
        results.add(parent.toAlignmentResult(AlignmentAction.SKIPPED, reason = skipReason))
        return@forEach
      }
      val name = parentName!!

      val sameNamedChildren = liveChildren
        .filter { it.localName?.let(::normaliseName) == normaliseName(name) }
        .sortedBy { it.getPathHierarchy() }
      val parentServices = parent.services.map { it.serviceType }.sorted()

      if (sameNamedChildren.size == 1) {
        val child = sameNamedChildren.single()
        val childServices = child.services.map { it.serviceType }.toSet()
        val missingServices = parentServices.filter { it !in childServices }
        if (missingServices.isEmpty()) {
          results.add(parent.toAlignmentResult(AlignmentAction.NO_ACTION))
          return@forEach
        }
        if (!request.dryRun) {
          val linkedTransaction = commonLocationService.createLinkedTransaction(
            prisonId = prisonId,
            TransactionType.LOCATION_UPDATE_NON_RESI,
            "Add services ${missingServices.joinToString(", ")} to ${child.getKey()} to match its parent ${parent.getKey()}",
          )
          child.update(
            PatchNonResidentialLocationRequest(servicesUsingLocation = childServices + missingServices),
            commonLocationService.getUsername(),
            clock,
            linkedTransaction,
          )
          commonLocationService.trackLocationUpdate(child, "Added parent services to Non-Residential Location with parent name")
          amended.add(child.toDto())
          linkedTransaction.txEndTime = LocalDateTime.now(clock)
        }
        results.add(
          parent.toAlignmentResult(
            AlignmentAction.ADD_SERVICES_TO_CHILD,
            servicesAddedToChild = ServicesAddedToChild(id = child.id!!, key = child.getKey(), name = child.localName!!, servicesAdded = missingServices),
          ),
        )
        return@forEach
      }

      val renames = if (sameNamedChildren.size > 1) {
        var suffix = 0
        sameNamedChildren.map { child ->
          var newName: String
          do {
            newName = numberedName(name, ++suffix)
          } while (normaliseName(newName) in usedNames)
          usedNames.add(normaliseName(newName))
          ChildRename(id = child.id!!, key = child.getKey(), oldName = child.localName!!, newName = newName)
        }
      } else {
        emptyList()
      }
      val action = if (renames.isEmpty()) AlignmentAction.CREATE_CHILD else AlignmentAction.RENAME_CHILDREN_AND_CREATE_CHILD

      if (request.dryRun) {
        results.add(parent.toAlignmentResult(action, renamedChildren = renames, createdChild = CreatedChild(id = null, key = null, name = name, services = parentServices)))
        return@forEach
      }

      val username = commonLocationService.getUsername()
      val linkedTransaction = commonLocationService.createLinkedTransaction(
        prisonId = prisonId,
        TransactionType.LOCATION_UPDATE_NON_RESI,
        "Align child locations of ${parent.getKey()} to parent name '$name'",
      )
      renames.forEach { rename ->
        val child = sameNamedChildren.first { it.id == rename.id }
        child.updateLocalName(rename.newName, username, clock, linkedTransaction)
        commonLocationService.trackLocationUpdate(child, "Renamed Non-Residential Location to align with parent name")
        amended.add(child.toDto())
      }

      val code = generateUniqueNonResidentialCode(prisonId, name, parent.getPathHierarchy())
      val savedChild = createChildOfParent(parent, code, parentServices, linkedTransaction)
      commonLocationService.trackLocationUpdate(savedChild, "Created Non-Residential Location with parent name")
      created.add(savedChild.toDto())
      linkedTransaction.txEndTime = LocalDateTime.now(clock)

      results.add(
        parent.toAlignmentResult(
          action,
          renamedChildren = renames,
          createdChild = CreatedChild(id = savedChild.id, key = savedChild.getKey(), name = name, services = parentServices),
        ),
      )
    }

    log.info("Aligned child locations to parent name in $prisonId (dryRun=${request.dryRun}): ${results.groupingBy { it.action }.eachCount()}")
    return AlignChildrenToParentNameResult(
      report = AlignChildrenToParentNameReport(prisonId = prisonId, dryRun = request.dryRun, parents = results),
      created = created,
      amended = amended,
    )
  }

  private fun normaliseName(name: String) = name.trim().lowercase()

  /**
   * "[name] [number]", shortening [name] where needed so the result still fits the local name column.
   */
  private fun numberedName(name: String, number: Int): String {
    val suffix = " $number"
    return name.take(MAX_LOCAL_NAME_LENGTH - suffix.length).trimEnd() + suffix
  }

  private fun NonResidentialLocation.toAlignmentResult(
    action: AlignmentAction,
    reason: String? = null,
    renamedChildren: List<ChildRename> = emptyList(),
    createdChild: CreatedChild? = null,
    servicesAddedToChild: ServicesAddedToChild? = null,
  ) = ParentAlignmentResult(
    parentId = id!!,
    parentKey = getKey(),
    parentName = localName,
    action = action,
    reason = reason,
    renamedChildren = renamedChildren,
    createdChild = createdChild,
    servicesAddedToChild = servicesAddedToChild,
  )

  /**
   * Creates and saves a non-residential child of [parent] with the parent's name, type and status, used by
   * [services]. The caller owns [linkedTransaction] and sets its end time.
   */
  private fun createChildOfParent(
    parent: NonResidentialLocation,
    code: String,
    services: Collection<ServiceType>,
    linkedTransaction: LinkedTransaction,
  ): NonResidentialLocation {
    val username = commonLocationService.getUsername()
    val newLocation = NonResidentialLocation(
      id = null,
      code = code,
      pathHierarchy = code, // will be updated by setParent
      locationType = parent.locationType,
      prisonId = parent.prisonId,
      status = parent.status,
      parent = parent,
      localName = parent.localName,
      childLocations = sortedSetOf(),
      whenCreated = LocalDateTime.now(clock),
      createdBy = username,
      internalMovementAllowed = services.contains(ServiceType.INTERNAL_MOVEMENTS),
    ).apply {
      parent.addChildLocation(this)
      services.forEach { serviceType ->
        serviceType.nonResidentialUsageType?.let { addUsage(it, 99) }
        addService(serviceType)
      }
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = username,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
    }
    return nonResidentialLocationRepository.save(newLocation)
  }

  @Transactional
  fun createBasicNonResidentialLocation(prisonId: String, request: CreateOrUpdateNonResidentialLocationRequest): NonResidentialLocationDTO {
    if (request.localName == null) {
      throw ValidationException("localName must be provided when creating a non-residential location")
    }

    validateLocalNameNotDuplicated(prisonId, request.localName)

    val code = generateUniqueNonResidentialCode(prisonId, request.localName)

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = prisonId,
      TransactionType.LOCATION_CREATE_NON_RESI,
      "Create non-residential location $code in prison $prisonId",
    )

    val locationToCreate = request.toNewEntity(prisonId = prisonId, code = code, createdBy = commonLocationService.getUsername(), clock = clock, linkedTransaction)
    val createdLocation = nonResidentialLocationRepository.save(locationToCreate)

    log.info("Non-residential location ${createdLocation.id} created")
    commonLocationService.trackLocationUpdate(createdLocation, "Created Non-Residential Location")

    return createdLocation.toNonResidentialDto().also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  /**
   * Generates a code for a new location that is not already taken. For a child location, pass [parentPath] so the
   * check also covers the child's full path, which is what must be unique within the prison.
   */
  private fun generateUniqueNonResidentialCode(prisonId: String, localName: String, parentPath: String? = null): String {
    var checksumDigits = 2
    var code = generateNonResidentialCode(
      prisonId = prisonId,
      localName = localName,
      checksumDigits = checksumDigits,
      maxSize = 6 + checksumDigits,
    )

    fun isTaken(code: String) = nonResidentialLocationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, code) != null ||
      (parentPath != null && nonResidentialLocationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, "$parentPath-$code") != null)

    while (isTaken(code)) {
      checksumDigits++
      if (checksumDigits > 6) {
        throw RuntimeException("Unable to generate unique code for non-residential location $localName in prison $prisonId")
      }
      code = generateNonResidentialCode(
        prisonId = prisonId,
        localName = localName,
        checksumDigits = checksumDigits,
        maxSize = 6 + checksumDigits,
      )
    }

    return code
  }

  @Transactional
  fun updateNonResidentialLocation(
    id: UUID,
    updateRequest: CreateOrUpdateNonResidentialLocationRequest,
  ): Pair<NonResidentialLocationDTO, AuditType> {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    if (nonResLocation.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(nonResLocation.getKey())
    }

    updateRequest.localName?.let { localName ->
      if (localName.lowercase() != nonResLocation.localName?.lowercase()) {
        validateLocalNameNotDuplicated(nonResLocation.prisonId, localName, nonResLocation.id!!)
      }
    }

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    nonResLocation.update(
      PatchNonResidentialLocationRequest(
        localName = updateRequest.localName,
        servicesUsingLocation = updateRequest.servicesUsingLocation,
      ),
      commonLocationService.getUsername(),
      clock,
      linkedTransaction,
    )

    var auditType = AuditType.LOCATION_AMENDED
    val username = commonLocationService.getUsername()
    updateRequest.active?.let { activate ->
      if (activate) {
        if (activateLocation(nonResLocation, username, linkedTransaction)) {
          auditType = AuditType.LOCATION_REACTIVATED
        }
      } else {
        if (deactivateLocation(nonResLocation, username, linkedTransaction)) {
          auditType = AuditType.LOCATION_DEACTIVATED
        }
      }
    }

    commonLocationService.trackLocationUpdate(nonResLocation, "Updated non-residential location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)
    return Pair(nonResLocation.toNonResidentialDto(), auditType)
  }

  private fun activateLocation(location: NonResidentialLocation, username: String, linkedTransaction: LinkedTransaction): Boolean {
    if (location.isActive()) return false

    location.reactivate(
      userOrSystemInContext = username,
      clock = clock,
      linkedTransaction = linkedTransaction,
    )
    return true
  }

  private fun deactivateLocation(location: NonResidentialLocation, username: String, linkedTransaction: LinkedTransaction): Boolean {
    if (!location.isActive()) return false

    location.temporarilyDeactivate(
      deactivatedReason = DeactivatedReason.OTHER,
      deactivatedDate = LocalDateTime.now(clock),
      deactivationReasonDescription = "Non residential location - deactivated",
      userOrSystemInContext = username,
      linkedTransaction = linkedTransaction,
    )
    return true
  }

  private fun validateLocalNameNotDuplicated(prisonId: String, localName: String, locationId: UUID? = null) {
    if (nonResidentialLocationRepository.findAllByPrisonIdAndLocalName(prisonId = prisonId, localName = localName)
        .any { !it.isPermanentlyDeactivated() && (locationId == null || it.id != locationId) }
    ) {
      throw DuplicateNonResidentialLocalNameInPrisonException(prisonId = prisonId, localName = localName)
    }
  }

  @Transactional
  fun createNonResidentialLocation(request: CreateNonResidentialLocationRequest): LocationDTO {
    val parentLocation = getParentLocation(request.parentId)

    commonLocationService.checkParentValid(
      parentLocation = parentLocation,
      code = request.code,
      prisonId = request.prisonId,
    ) // check that code doesn't clash with the existing location

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = request.prisonId,
      TransactionType.LOCATION_CREATE_NON_RESI,
      "Create non-residential location ${request.code} in prison ${request.prisonId} under ${parentLocation?.getKey() ?: "top level"}",
    )

    val locationToCreate = request.toNewEntity(commonLocationService.getUsername(), clock, linkedTransaction, parentLocation)

    val servicesChanged = request.servicesUsingLocation != null

    val createdLocation = nonResidentialLocationRepository.save(locationToCreate)

    log.info("Created Non-Residential Location [${createdLocation.getKey()}]")
    commonLocationService.trackLocationUpdate(createdLocation, "Created Non-Residential Location")

    return createdLocation.toDto(includeParent = servicesChanged).also {
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    id: UUID,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    return commonLocationService.patchLocation(nonResLocation, patchLocationRequest, linkedTransaction).also {
      commonLocationService.trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  @Transactional
  fun updateNonResidentialLocation(
    key: String,
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): UpdateLocationResult {
    val nonResLocation = nonResidentialLocationRepository.findOneByKey(key) ?: throw LocationNotFoundException(key)
    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    return commonLocationService.patchLocation(nonResLocation, patchLocationRequest, linkedTransaction).also {
      commonLocationService.trackLocationUpdate(it.location)
      linkedTransaction.txEndTime = LocalDateTime.now(clock)
    }
  }

  /**
   * Removes a parent location from the non-residential locations list.
   *
   * Presented to users as archiving, but nothing is deactivated: the location keeps its status, its
   * children are untouched and every service-facing endpoint carries on as before. Only permitted
   * for a parent location that no service uses - a leaf location should be archived properly
   * instead, and a location a service still uses has to stay visible so it can be maintained.
   */
  @Transactional
  fun hideFromList(id: UUID): NonResidentialLocationDTO {
    val location = nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    if (location.isLeafLevel()) {
      throw LocationCannotBeHiddenFromListException(location.getKey(), "it is not a parent location, archive it instead")
    }
    if (location.isHiddenFromList()) {
      throw LocationCannotBeHiddenFromListException(location.getKey(), "it is already hidden from the list")
    }
    if (location.services.isNotEmpty()) {
      throw LocationCannotBeHiddenFromListException(
        location.getKey(),
        "it is still used by ${location.services.joinToString(", ") { it.serviceType.description }}",
      )
    }

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = location.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Hiding non-residential location ${location.getKey()} from the list",
    )

    location.hideFromList(commonLocationService.getUsername(), clock, linkedTransaction)

    log.info("Non-residential location ${location.getKey()} hidden from the list")
    commonLocationService.trackLocationUpdate(location, "Hid Non-Residential Location from the list")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)

    return location.toNonResidentialDto()
  }

  private fun getParentLocation(parentId: UUID?): Location? = parentId?.let {
    locationRepository.findById(parentId).getOrNull()
      ?: throw LocationNotFoundException(it.toString())
  }

  fun getNonResidentialLocationSummaryForPrison(
    prisonId: String,
    statuses: List<LocationStatus> = emptyList(),
    serviceFamilyTypes: List<ServiceFamilyType> = emptyList(),
    serviceTypes: List<ServiceType> = emptyList(),
    searchByLocalName: String? = null,
    filterParents: Boolean = false,
    includeProperty: Boolean = false,
    locationTypes: List<NonResidentialLocationType> = emptyList(),
    pageable: Pageable = PageRequest.of(0, 100, Sort.by("localName").ascending()),
  ): NonResidentialSummary {
    // The requested service families and service types, resolved to a single set of service types:
    // families expand to their member service types and are combined (unioned) with any service
    // types requested directly. In practice the UI sends one or the other, but both may be supplied.
    val allServiceTypes = (serviceFamilyTypes.flatMap { it.getServiceTypes() } + serviceTypes).distinct()

    val specification = Specification.allOf(
      buildList {
        add(filterByPrisonId(prisonId))
        if (filterParents) {
          add(filterByIsLeaf())
        }
        add(excludeByCode("RTU"))
        if (!includeProperty) {
          add(excludePropertyOnlyLocations())
        }

        searchByLocalName?.let {
          add(filterByLocalName(it))
        }
        if (allServiceTypes.isNotEmpty()) {
          add(filterByServiceTypes(allServiceTypes))
          // A parent only exposes the services that are editable at parent level; a
          // non-parent-editable service it is associated with is not shown on the parent. So when
          // none of the requested services are parent-editable, restrict to leaf locations rather
          // than returning parents that would appear to use nothing.
          if (!filterParents && allServiceTypes.all { !it.serviceFamily.editableInParent }) {
            add(filterByIsLeaf())
          }
        }
        if (statuses.isNotEmpty()) {
          add(filterByStatusesTreatingHiddenAsArchived(statuses))
        }
        if (locationTypes.isNotEmpty()) {
          add(filterByTypes(locationTypes.map { it.baseType }))
        }
      },
    )

    val locations = nonResidentialLocationRepository.findAll(specification, pageable).map { it.toNonResidentialDto() }
    return NonResidentialSummary(prisonId = prisonId, locations = locations)
  }
}

/**
 * The outcome of making a property storage location available: the slim [propertyLocation] DTO for the
 * caller, the full [location] DTO for the domain event, and whether an existing location's designation was
 * [reinstated] rather than a new location created. The caller uses [reinstated] to report the right status and
 * to raise an amended rather than a created event.
 */
data class PropertyLocationWriteResult(
  val propertyLocation: PropertyLocationDto,
  val location: NonResidentialLocationDTO,
  val reinstated: Boolean,
)

@Schema(description = "Request to give each parent non-residential location one child with the same name")
data class AlignChildrenToParentNameRequest(
  @param:Schema(description = "When true (the default), nothing is changed and the report shows what would be done", example = "true")
  val dryRun: Boolean = true,
  @param:Schema(description = "Limit the run to these parent location IDs. When omitted, every parent in the prison is considered")
  val parentLocationIds: Set<UUID>? = null,
)

enum class AlignmentAction {
  CREATE_CHILD,
  RENAME_CHILDREN_AND_CREATE_CHILD,
  ADD_SERVICES_TO_CHILD,
  NO_ACTION,
  SKIPPED,
}

@Schema(description = "A child location renamed so that only one child has the parent's name")
data class ChildRename(
  val id: UUID,
  val key: String,
  val oldName: String,
  val newName: String,
)

@Schema(description = "The child location created with the parent's name. Id and key are empty on a dry run")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatedChild(
  val id: UUID?,
  val key: String?,
  val name: String,
  val services: List<ServiceType>,
)

@Schema(description = "The existing child with the parent's name, and the parent's services added to it")
data class ServicesAddedToChild(
  val id: UUID,
  val key: String,
  val name: String,
  val servicesAdded: List<ServiceType>,
)

@Schema(description = "What was done, or would be done on a dry run, for one parent location")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ParentAlignmentResult(
  val parentId: UUID,
  val parentKey: String? = null,
  val parentName: String? = null,
  val action: AlignmentAction,
  val reason: String? = null,
  val renamedChildren: List<ChildRename> = emptyList(),
  val createdChild: CreatedChild? = null,
  val servicesAddedToChild: ServicesAddedToChild? = null,
)

@Schema(description = "Report of aligning child locations to their parent's name in a prison")
data class AlignChildrenToParentNameReport(
  val prisonId: String,
  val dryRun: Boolean,
  val parents: List<ParentAlignmentResult>,
) {
  @get:Schema(description = "Number of parents for each action")
  val summary: Map<AlignmentAction, Int>
    get() = AlignmentAction.entries.associateWith { action -> parents.count { it.action == action } }
}

/**
 * The [report] returned to the caller, plus the locations [created] and [amended] (renamed, or given the parent's
 * services) so the caller can publish events.
 */
data class AlignChildrenToParentNameResult(
  val report: AlignChildrenToParentNameReport,
  val created: List<LocationDTO>,
  val amended: List<LocationDTO>,
)

@Schema(description = "Non Residential Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NonResidentialSummary(
  @param:Schema(description = "Prison Id", required = true)
  val prisonId: String,
  @param:Schema(description = "All non-residential locations for this prison")
  val locations: Page<NonResidentialLocationDTO>,
)

@Schema(description = "Non Residential Detail")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class NonResidentialLocationDTO(
  @param:Schema(description = "Location Id", example = "2475f250-434a-4257-afe7-b911f1773a4d", required = true)
  val id: UUID,

  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @param:Schema(
    description = "Description to display for location",
    example = "Gym",
    required = true,
  )
  val localName: String? = null,

  @param:Schema(description = "Location Code", example = "001", required = true)
  val code: String,

  @param:Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String,

  @param:Schema(description = "Indicates this is the lowest level, and not a parent", example = "true", required = true)
  val isLeafLevel: Boolean = false,

  @param:Schema(description = "Location Type", example = "ADJUDICATION_ROOM", required = true)
  val locationType: LocationType,

  @param:Schema(description = "Indicates if the location is permanently inactive", example = "false", required = true)
  val permanentlyInactive: Boolean = false,

  @param:Schema(description = "Reason for permanently deactivating", example = "Demolished", required = false)
  val permanentlyInactiveReason: String? = null,

  @param:Schema(description = "Collections of services that use this location", required = true)
  val usedByGroupedServices: List<ServiceFamilyType> = emptyList(),

  @param:Schema(description = "Services that use this location", required = true)
  val usedByServices: List<ServiceType> = emptyList(),

  @param:Schema(description = "Status of the location", example = "ACTIVE", required = true)
  val status: DerivedLocationStatus,

  @param:Schema(description = "Date the location was deactivated", example = "2023-01-23T12:23:00", required = false)
  val deactivatedDate: LocalDateTime? = null,

  @param:Schema(description = "Reason for deactivation", example = "DAMAGED", required = false)
  val deactivatedReason: DeactivatedReason? = null,

  @param:Schema(
    description = "For OTHER deactivation reason, a free text comment is provided",
    example = "Window damage",
    required = false,
  )
  val deactivationReasonDescription: String? = null,

  @param:Schema(description = "Staff username who deactivated the location", required = false)
  val deactivatedBy: String? = null,

  @param:Schema(
    description = "Current Level within hierarchy, starts at 1, e.g Wing = 1",
    examples = ["1", "2", "3"],
    required = true,
  )
  val level: Int,

  @param:Schema(description = "Parent Location Id", example = "57718979-573c-433a-9e51-2d83f887c11c", required = false)
  val parentId: UUID?,

  @param:Schema(description = "Location hierarchy (ancestors and self), top to bottom", required = false)
  val locationHierarchy: List<LocationSummary>? = null,

  @param:Schema(
    description = "Indicates a user has removed this location from the non-residential locations list. " +
      "Display only - the location is not deactivated and remains available to any service using it.",
    example = "false",
    required = true,
  )
  val hiddenFromList: Boolean = false,

  @param:Schema(
    description = "Indicates this location can be removed from the non-residential locations list, " +
      "i.e. it is a parent location that no service uses",
    example = "false",
    required = true,
  )
  val canBeHiddenFromList: Boolean = false,
) {
  @Schema(description = "Key for a location", example = "MDI-ADJU", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"
}

/** Size of the location.local_name column. */
private const val MAX_LOCAL_NAME_LENGTH = 80

/**
 * Generates a unique code from the local name by extracting consonants and adding a checksum.
 * The code is the maximum 8 characters: up to 6 consonants + 2 digit checksum by default
 *
 * @param prisonId The prison ID to include in the checksum calculation for uniqueness
 * @return Generated code (max 8 characters)
 */
fun generateNonResidentialCode(
  prisonId: String,
  localName: String,
  numberOfConsonants: Int = 6,
  checksumDigits: Int = 2,
  maxSize: Int = 8,
): String {
  // Extract consonants from the localName (uppercase letters only, excluding vowels)
  val consonants = localName
    .uppercase()
    .filter { it.isLetter() && it !in setOf('A', 'E', 'I', 'O', 'U') }
    .take(numberOfConsonants) // Take up to `numberOfConsonants` consonants to leave room for `checksumDigits` digit checksum

  // If no consonants found, use first alphanumeric characters
  val baseCode = consonants.ifEmpty {
    localName.filter { it.isLetterOrDigit() }.uppercase().take(numberOfConsonants)
  }

  // Calculate checksum from prisonId + localName to ensure uniqueness within prison
  val checksum = calculateChecksum(prisonId, localName, checksumDigits)

  // Combine base code with checksum, ensuring max 8 characters
  val maxBaseLength = numberOfConsonants.coerceAtMost(maxSize - checksumDigits) // Leave room for `checksumDigits` checksum
  return baseCode.take(maxBaseLength) + checksum.toString().padStart(checksumDigits, '0')
}

/**
 * Calculates a 2-digit checksum (00-99) from prisonId and localName.
 * Uses a simple hash-based algorithm for consistency.
 */
private fun calculateChecksum(prisonId: String, localName: String, checksumDigits: Int): Int {
  val combined = "$prisonId:$localName"
  var hash = 0

  combined.forEach { char ->
    hash = (hash * 31 + char.code) % 10.0.pow(checksumDigits).toInt()
  }

  return abs(hash)
}
