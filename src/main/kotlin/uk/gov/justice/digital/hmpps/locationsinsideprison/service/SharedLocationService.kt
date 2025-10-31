package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationAlreadyExistsException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LockedLocationCannotBeUpdatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PermanentlyDeactivatedUpdateNotAllowedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService.Companion.log
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Component
class SharedLocationService(
  private val locationRepository: LocationRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val telemetryClient: TelemetryClient,
  private val clock: Clock,
) {

  fun getUsername() = authenticationHolder.username ?: SYSTEM_USERNAME

  fun createLinkedTransaction(prisonId: String, type: TransactionType, detail: String, transactionInvokedBy: String? = null): LinkedTransaction {
    val linkedTransaction = LinkedTransaction(
      prisonId = prisonId,
      transactionType = type,
      transactionDetail = detail,
      transactionInvokedBy = transactionInvokedBy ?: getUsername(),
      txStartTime = LocalDateTime.now(clock),
    )
    return linkedTransactionRepository.save(linkedTransaction).also {
      log.info("Created linked transaction: $it")
    }
  }

  fun patchLocation(
    location: Location,
    patchLocationRequest: PatchLocationRequest,
    linkedTransaction: LinkedTransaction,
  ): UpdateLocationResult {
    if (location.isLocationLocked()) {
      throw LockedLocationCannotBeUpdatedException(location.getKey())
    }

    val (codeChanged, oldParent, parentChanged) = updateCoreLocationDetails(location, patchLocationRequest, linkedTransaction)

    location.update(patchLocationRequest, getUsername(), clock, linkedTransaction)

    return UpdateLocationResult(
      location.toDto(
        includeChildren = codeChanged || parentChanged,
        includeParent = parentChanged,
        includeNonResidential = false,
      ),
      if (parentChanged && oldParent != null) oldParent.toDto(includeParent = true) else null,
    )
  }

  fun updateCoreLocationDetails(
    locationToUpdate: Location,
    patchLocationRequest: PatchLocationRequest,
    linkedTransaction: LinkedTransaction,
  ): UpdatedSummary {
    if (locationToUpdate.isPermanentlyDeactivated()) {
      throw PermanentlyDeactivatedUpdateNotAllowedException(locationToUpdate.getKey())
    }

    val codeChanged = patchLocationRequest.code != null && patchLocationRequest.code != locationToUpdate.getLocationCode()
    val oldParent = locationToUpdate.getParent()
    val parentChanged = when {
      patchLocationRequest.removeParent == true ->
        patchLocationRequest.parentId == null && patchLocationRequest.parentLocationKey == null
      patchLocationRequest.parentId != null ->
        patchLocationRequest.parentId != oldParent?.id
      patchLocationRequest.parentLocationKey != null ->
        patchLocationRequest.parentLocationKey != oldParent?.getKey()
      else -> false
    }

    if (codeChanged || parentChanged) {
      val newCode = patchLocationRequest.code ?: locationToUpdate.getLocationCode()

      val theParent = when {
        patchLocationRequest.removeParent == true -> null
        patchLocationRequest.parentId != null -> locationRepository.findById(patchLocationRequest.parentId!!)
          .getOrNull() ?: throw LocationNotFoundException(patchLocationRequest.parentId.toString())
        patchLocationRequest.parentLocationKey != null -> locationRepository.findOneByKey(patchLocationRequest.parentLocationKey!!)
          ?: throw LocationNotFoundException(patchLocationRequest.parentLocationKey!!)
        else -> oldParent
      }

      checkParentValid(theParent, newCode, locationToUpdate.prisonId)

      if (parentChanged && theParent?.id == locationToUpdate.id) throw ValidationException("Cannot set parent to self")
      locationToUpdate.setParent(theParent)

      if (parentChanged) {
        locationToUpdate.addHistory(
          LocationAttribute.PARENT_LOCATION,
          oldParent?.id?.toString(),
          theParent?.id?.toString(),
          getUsername(),
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
    }
    return UpdatedSummary(codeChanged = codeChanged, oldParent = oldParent, parentChanged = parentChanged)
  }

  fun checkParentValid(
    parentLocation: Location?,
    code: String,
    prisonId: String,
  ) {
    val pathHierarchy = buildNewPathHierarchy(parentLocation, code)

    locationRepository.findOneByPrisonIdAndPathHierarchy(prisonId, pathHierarchy)
      ?.let { throw LocationAlreadyExistsException("$prisonId-$pathHierarchy") }
  }

  fun trackLocationUpdate(location: Location, trackDescription: String) {
    telemetryClient.trackEvent(
      trackDescription,
      mapOf(
        "id" to location.id!!.toString(),
        "prisonId" to location.prisonId,
        "path" to location.getPathHierarchy(),
      ),
      null,
    )
  }

  fun trackLocationUpdate(location: LocationDTO) {
    telemetryClient.trackEvent(
      "Updated Location",
      mapOf(
        "id" to location.id.toString(),
        "prisonId" to location.prisonId,
        "path" to location.pathHierarchy,
      ),
      null,
    )
  }

  private fun buildNewPathHierarchy(parentLocation: Location?, code: String) = if (parentLocation != null) {
    parentLocation.getPathHierarchy() + "-"
  } else {
    ""
  } + code
}
