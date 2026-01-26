package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateOrUpdateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.excludeByLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByLocalName
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByPrisonId
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByStatuses
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DuplicateNonResidentialLocalNameInPrisonException
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
      .filter { !it.isPermanentlyDeactivated() }
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

  fun findByPrisonIdAndLocalName(prisonId: String, localName: String) = nonResidentialLocationRepository.findAllByPrisonIdAndLocalName(prisonId, localName)
    .filter { !it.isPermanentlyDeactivated() }
    .map { it.toDto() }
    .firstOrNull() ?: throw LocationNotFoundException("No location found with prisonId $prisonId and localName $localName")

  fun getByPrisonAndUsageType(
    prisonId: String,
    usageType: NonResidentialUsageType? = null,
    sortByLocalName: Boolean = false,
    formatLocalName: Boolean = false,
    filterParents: Boolean = true,
  ): List<LocationDTO> {
    val filteredByUsage = usageType?.let {
      nonResidentialLocationRepository.findAllByPrisonIdAndNonResidentialUsages(prisonId, usageType)
    } ?: nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialUsages(prisonId)

    val filteredResults = filteredByUsage
      .filter { it.getLocationCode() != "RTU" }
      .filter { !it.isPermanentlyDeactivated() }
      .filter { !filterParents || it.findSubLocations().intersect(filteredByUsage.toSet()).isEmpty() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      filteredResults.sortedBy { it.localName }
    } else {
      filteredResults
    }
  }

  @Transactional
  fun createChildLocationsForServicesWithParent(prisonId: String): List<LocationDTO> {
    val allNonResLocations = nonResidentialLocationRepository.findAllByPrisonIdWithNonResidentialServices(prisonId)
    val locationsWithChildren = allNonResLocations.filter { it.findSubLocations().isNotEmpty() }
    val createdLocations = mutableListOf<LocationDTO>()

    locationsWithChildren.forEach { parent ->
      val parentServices = parent.services.map { it.serviceType }.toSet()
      if (parentServices.isNotEmpty()) {
        val descendants = parent.findSubLocations()
        val missingServices = parentServices.filter { serviceType ->
          descendants.none { child ->
            (child as? NonResidentialLocation)?.services?.any { it.serviceType == serviceType } == true
          }
        }

        if (missingServices.isNotEmpty()) {
          val localName = parent.localName ?: parent.getLocationCode()
          val code = generateUniqueNonResidentialCode(prisonId, localName)

          val linkedTransaction = commonLocationService.createLinkedTransaction(
            prisonId = prisonId,
            TransactionType.LOCATION_CREATE_NON_RESI,
            "Create non-residential location $code as child of ${parent.getKey()} for services ${missingServices.joinToString(", ")}",
          )

          val username = commonLocationService.getUsername()
          val newLocation = NonResidentialLocation(
            id = null,
            code = code,
            pathHierarchy = code, // will be updated by setParent
            locationType = parent.locationType,
            prisonId = prisonId,
            status = LocationStatus.ACTIVE,
            parent = parent,
            localName = parent.localName,
            childLocations = sortedSetOf(),
            whenCreated = LocalDateTime.now(clock),
            createdBy = username,
            internalMovementAllowed = missingServices.contains(ServiceType.INTERNAL_MOVEMENTS),
          ).apply {
            parent.addChildLocation(this)
            missingServices.forEach { serviceType ->
              addService(serviceType)
              addUsage(serviceType.nonResidentialUsageType, 99)
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
          val savedLocation = nonResidentialLocationRepository.save(newLocation)
          commonLocationService.trackLocationUpdate(savedLocation, "Created Non-Residential Location for services ${missingServices.joinToString(", ")}")
          linkedTransaction.txEndTime = LocalDateTime.now(clock)
          createdLocations.add(savedLocation.toDto())
        }
      }
    }
    log.info("Created ${createdLocations.size} child locations for services with parent")
    return createdLocations
  }

  @Transactional
  fun createBasicNonResidentialLocation(prisonId: String, request: CreateOrUpdateNonResidentialLocationRequest): NonResidentialLocationDTO {
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

  private fun generateUniqueNonResidentialCode(prisonId: String, localName: String): String {
    var checksumDigits = 2
    var code = generateNonResidentialCode(
      prisonId = prisonId,
      localName = localName,
      checksumDigits = checksumDigits,
      maxSize = 6 + checksumDigits,
    )

    while (nonResidentialLocationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, code) != null) {
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
  ): Pair<NonResidentialLocationDTO, List<InternalLocationDomainEventType>> {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

    if (nonResLocation.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(nonResLocation.getKey())
    }
    validateLocalNameNotDuplicated(nonResLocation.prisonId, updateRequest.localName, nonResLocation.id!!)

    val linkedTransaction = commonLocationService.createLinkedTransaction(
      prisonId = nonResLocation.prisonId,
      TransactionType.LOCATION_UPDATE_NON_RESI,
      "Update non-residential location ${nonResLocation.getKey()}",
    )

    val events = mutableListOf<InternalLocationDomainEventType>()
    nonResLocation.update(
      PatchNonResidentialLocationRequest(
        localName = updateRequest.localName,
        servicesUsingLocation = updateRequest.servicesUsingLocation,
      ),
      commonLocationService.getUsername(),
      clock,
      linkedTransaction,
    )
    events.add(InternalLocationDomainEventType.LOCATION_AMENDED)

    val username = commonLocationService.getUsername()
    updateRequest.active?.let { activate ->
      if (activate) {
        if (activateLocation(nonResLocation, username, linkedTransaction)) {
          events.add(InternalLocationDomainEventType.LOCATION_REACTIVATED)
        }
      } else {
        if (deactivateLocation(nonResLocation, username, linkedTransaction)) {
          events.add(InternalLocationDomainEventType.LOCATION_DEACTIVATED)
        }
      }
    }

    commonLocationService.trackLocationUpdate(nonResLocation, "Updated non-residential location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)
    return Pair(nonResLocation.toNonResidentialDto(), events)
  }

  private fun activateLocation(location: Location, username: String, linkedTransaction: LinkedTransaction): Boolean {
    if (location.isActive()) return false

    location.reactivate(
      userOrSystemInContext = username,
      clock = clock,
      linkedTransaction = linkedTransaction,
    )
    return true
  }

  private fun deactivateLocation(location: Location, username: String, linkedTransaction: LinkedTransaction): Boolean {
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

  private fun validateLocalNameNotDuplicated(prisonId: String, localName: String) {
    if (nonResidentialLocationRepository.findAllByPrisonIdAndLocalName(prisonId = prisonId, localName = localName).any()) {
      throw DuplicateNonResidentialLocalNameInPrisonException(prisonId = prisonId, localName = localName)
    }
  }

  private fun validateLocalNameNotDuplicated(prisonId: String, localName: String, locationId: UUID) {
    if (nonResidentialLocationRepository.findAllByPrisonIdAndLocalName(prisonId = prisonId, localName = localName).any { it.id != locationId }) {
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

  private fun getParentLocation(parentId: UUID?): Location? = parentId?.let {
    locationRepository.findById(parentId).getOrNull()
      ?: throw LocationNotFoundException(it.toString())
  }

  fun getNonResidentialLocationSummaryForPrison(
    prisonId: String,
    statuses: List<LocationStatus> = emptyList(),
    serviceType: ServiceType? = null,
    searchByLocalName: String? = null,
    locationTypes: List<NonResidentialLocationType> = emptyList(),
    pageable: Pageable = PageRequest.of(0, 100, Sort.by("localName").ascending()),
  ): NonResidentialSummary {
    val specification = Specification.allOf(
      buildList {
        add(filterByPrisonId(prisonId))
        add(excludeByCode("RTU"))
        add(excludeByLocationType(LocationType.BOX))

        searchByLocalName?.let {
          add(filterByLocalName(it))
        }
        serviceType?.let {
          add(filterByServiceType(it))
        }
        if (statuses.isNotEmpty()) {
          add(filterByStatuses(statuses))
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
) {
  @Schema(description = "Key for a location", example = "MDI-ADJU", required = true)
  fun getKey(): String = "$prisonId-$pathHierarchy"
}

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
