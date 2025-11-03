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
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceFamilyType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.excludeByCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.excludeByLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByPrisonId
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByStatuses
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification.filterByTypes
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService.Companion.log
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.isNotEmpty
import kotlin.jvm.optionals.getOrNull
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
      .filter { !filterParents || it.findSubLocations().intersect(filteredByUsage.toSet()).isEmpty() }
      .map { it.toDto(formatLocalName = formatLocalName) }

    return if (sortByLocalName) {
      filteredResults.sortedBy { it.localName }
    } else {
      filteredResults
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
  fun createBasicNonResidentialLocation(prisonId: String, request: CreateOrUpdateNonResidentialLocationRequest): NonResidentialLocationDTO {
    val code = request.generateCode(prisonId)
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
    id: UUID,
    updateRequest: CreateOrUpdateNonResidentialLocationRequest,
  ): NonResidentialLocationDTO {
    val nonResLocation =
      nonResidentialLocationRepository.findById(id).orElseThrow { LocationNotFoundException(id.toString()) }

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

    commonLocationService.trackLocationUpdate(nonResLocation, "Updated non-residential location")
    linkedTransaction.txEndTime = LocalDateTime.now(clock)
    return nonResLocation.toNonResidentialDto()
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
    locationTypes: List<NonResidentialLocationType> = emptyList(),
    pageable: Pageable = PageRequest.of(0, 100, Sort.by("localName").ascending()),
  ): NonResidentialSummary {
    val specification = Specification.allOf(
      buildList {
        add(filterByPrisonId(prisonId))
        add(excludeByCode("RTU"))
        add(excludeByLocationType(LocationType.BOX))

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

  @param:Schema(description = "Indicates the location is enabled", example = "true", required = true, deprecated = true)
  val active: Boolean = true,

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

)
