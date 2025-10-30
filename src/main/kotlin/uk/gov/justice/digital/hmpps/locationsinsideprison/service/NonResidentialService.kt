package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.NonResidentialLocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService.Companion.log
import java.time.Clock
import java.time.LocalDateTime
import java.util.*
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
      .filter { it.getCode() != "RTU" }
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
      .filter { it.getCode() != "RTU" }
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
}
