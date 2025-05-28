package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import jakarta.validation.ValidationException
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.exception.UserAuthorisationException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> {
    log.info("Validation exception: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(UserAuthorisationException::class)
  @ResponseStatus(FORBIDDEN)
  fun handleUserAuthorisationException(e: UserAuthorisationException): ResponseEntity<ErrorResponse> {
    log.error("Access denied exception: {}", e.message)
    return ResponseEntity
      .status(FORBIDDEN)
      .body(
        ErrorResponse(
          status = FORBIDDEN,
          userMessage = "User authorisation failure: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
  fun handleInvalidRequestFormatException(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> {
    log.info("Validation exception: Request format not supported: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
      .body(
        ErrorResponse(
          status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          userMessage = "Validation failure: Request format not supported: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleNoBodyValidationException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
    log.info("Validation exception: Couldn't read request body: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: Couldn't read request body: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
    val type = e.requiredType
    val message = if (type.isEnum) {
      "Parameter ${e.name} must be one of the following ${StringUtils.join(type.enumConstants, ", ")}"
    } else {
      "Parameter ${e.name} must be of type ${type.typeName}"
    }

    log.info("Validation exception: {}", message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: $message",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> {
    log.debug("Forbidden (403) returned with message {}", e.message)
    return ResponseEntity
      .status(HttpStatus.FORBIDDEN)
      .body(
        ErrorResponse(
          status = HttpStatus.FORBIDDEN,
          userMessage = "Forbidden: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(WebClientResponseException.NotFound::class)
  fun handleSpringNotFound(e: WebClientResponseException.NotFound): ResponseEntity<ErrorResponse?>? {
    log.debug("Not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("No resource found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "No resource found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(ResponseStatusException::class)
  fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse?>? {
    log.debug("Response status exception caught: {}", e.message)
    val reason = e.reason ?: "Unknown error"
    return ResponseEntity
      .status(e.statusCode)
      .body(
        ErrorResponse(
          status = e.statusCode.value(),
          userMessage = reason,
          developerMessage = reason,
        ),
      )
  }

  @ExceptionHandler(java.lang.Exception::class)
  fun handleException(e: java.lang.Exception): ResponseEntity<ErrorResponse?>? {
    log.error("Unexpected exception", e)
    return ResponseEntity
      .status(INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = INTERNAL_SERVER_ERROR,
          userMessage = "Unexpected error: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleInvalidMethodArgumentException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse>? {
    log.debug("MethodArgumentNotValidException exception caught: {}", e.message)

    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = ErrorCode.ValidationFailure,
          userMessage = "Validation Failure: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LocationNotFoundException::class)
  fun handleLocationNotFound(e: LocationNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.LocationNotFound,
          userMessage = "Location not found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(ApprovalRequestNotFoundException::class)
  fun handleApprovalRequestNotFound(e: ApprovalRequestNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Approval request not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.ApprovalRequestNotFound,
          userMessage = "Approval not found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LockedLocationCannotBeUpdatedException::class)
  fun handleLockedLocationCannotBeUpdated(e: LockedLocationCannotBeUpdatedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location LOCKED: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.LockedLocationCannotBeUpdated,
          userMessage = "Location LOCKED and cannot be updated: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(PendingApprovalAlreadyExistsException::class)
  fun handlePendingApprovalAlreadyExistsException(e: PendingApprovalAlreadyExistsException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location already pending an approval: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.ApprovalRequestAlreadyExists,
          userMessage = "Pending approval already exist for this location: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(TransactionNotFoundException::class)
  fun handleTransactionNotFound(e: TransactionNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Transaction not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.TransactionNotFound,
          userMessage = "Transaction not found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(PrisonNotFoundException::class)
  fun handlePrisonNotFound(e: PrisonNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Prison not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.LocationNotFound,
          userMessage = "Prison not found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LocationPrefixNotFoundException::class)
  fun handleLocationPrefixNotFound(e: LocationPrefixNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location prefix not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.LocationPrefixNotFound,
          userMessage = "${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(SignedOperationCapacityNotFoundException::class)
  fun handleOperationalCapacityNotFoundException(e: SignedOperationCapacityNotFoundException): ResponseEntity<ErrorResponse?>? {
    log.debug("Signed Operation Capacity not found exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          errorCode = ErrorCode.SignedOperationCapacityForPrisonNotFound,
          userMessage = "Signed Operation Capacity not found: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LocationAlreadyExistsException::class)
  fun handleLocationAlreadyExists(e: LocationAlreadyExistsException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location already exists exception caught: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.LocationAlreadyExists,
          userMessage = "Location already exists: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LocationCannotBeReactivatedException::class)
  fun handleLocationCannotBeReactivated(e: LocationCannotBeReactivatedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location cannot be re-activated: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = ErrorCode.LocationCannotBeReactivated,
          userMessage = "Location cannot be reactivated as parent inactive: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(ReasonForDeactivationMustBeProvidedException::class)
  fun handleReasonForDeactivationMustBeProvided(e: ReasonForDeactivationMustBeProvidedException): ResponseEntity<ErrorResponse?>? {
    log.debug("De-activating location requires a reason when using OTHER reason type: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = ErrorCode.OtherReasonNotProvidedForDeactivation,
          userMessage = "De-activating location requires a reason when using OTHER reason type: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(CapacityException::class)
  fun handleCapacityException(e: CapacityException): ResponseEntity<ErrorResponse?>? {
    log.warn("Capacity Validation: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = e.errorCode,
          userMessage = e.message,
          developerMessage = "Capacity Validation Error: ${e.message}",
        ),
      )
  }

  @ExceptionHandler(PermanentlyDeactivatedUpdateNotAllowedException::class)
  fun handlePermanentlyDeactivatedUpdateNotAllowedException(e: PermanentlyDeactivatedUpdateNotAllowedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Deactivated Location Exception: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.PermanentlyDeactivatedLocationCannotByUpdated,
          userMessage = "Deactivated Location Exception: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(UpdateNotAllowedAsConvertedCellException::class)
  fun handleUpdateNotAllowedAsConvertedCellException(e: UpdateNotAllowedAsConvertedCellException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location cannot be updated: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.LocationCannotByUpdatedAsConvertedCell,
          userMessage = "Location cannot be updated exception: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(ActiveLocationCannotBePermanentlyDeactivatedException::class)
  fun handleActiveLocationCannotBePermanentlyDeactivatedException(e: ActiveLocationCannotBePermanentlyDeactivatedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Attempt to perm deactivate an active location {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.LocationCannotBePermanentlyDeactivated,
          userMessage = "Permanent Deactivation Exception: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(LocationContainsPrisonersException::class)
  fun handleLocationContainsPrisonersException(e: LocationContainsPrisonersException): ResponseEntity<ErrorResponse?>? {
    log.debug("Cannot deactivate: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.DeactivationErrorLocationsContainPrisoners,
          userMessage = "Deactivation Exception: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  @ExceptionHandler(DuplicateLocalNameForSameHierarchyException::class)
  fun handleDuplicateLocalNameForSameHierarchyException(e: DuplicateLocalNameForSameHierarchyException): ResponseEntity<ErrorResponse?>? {
    log.debug("Duplicate Local name: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.DuplicateLocalNameAtSameLevel,
          userMessage = "Local name already exists in this prison at this level: ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class PrisonNotFoundException(id: String) : Exception("There is no prison found for ID = $id")

class LocationNotFoundException(id: String) : Exception("There is no location found for ID = $id")

class TransactionNotFoundException(txId: UUID) : Exception("There is no transaction found for txId = $txId")

class LocationPrefixNotFoundException(id: String) : Exception("Location prefix not found for $id")

class SignedOperationCapacityNotFoundException(prisonId: String) : Exception("There is no signed operation capacity found for prison ID = $prisonId")

class LocationAlreadyExistsException(key: String) : Exception("Location already exists = $key")
class ReasonForDeactivationMustBeProvidedException(key: String) : Exception("De-activating location $key requires a reason when using OTHER reason type")
class LocationCannotBeReactivatedException(key: String) : Exception("Location cannot be reactivated if parent is deactivated = $key")
class AlreadyDeactivatedLocationException(key: String) : ValidationException("$key: Cannot deactivate an already deactivated location")
class CapacityException(val key: String, override val message: String, val errorCode: ErrorCode) : ValidationException("$key: [Error Code: $errorCode] - Capacity Exception: $message")
class PermanentlyDeactivatedUpdateNotAllowedException(key: String) : ValidationException("Location $key cannot be updated as has been permanently deactivated")
class UpdateNotAllowedAsConvertedCellException(key: String) : ValidationException("Location $key cannot be updated as has been converted to non-res cell")
class LocationContainsPrisonersException(locationsWithPrisoners: Map<String, List<Prisoner>>) : Exception("${locationsWithPrisoners.keys.size} locations contain ${locationsWithPrisoners.values.size} prisoners")
class DuplicateLocalNameForSameHierarchyException(key: String, topLocationKey: String) : ValidationException("$key already the same local name in this hierarchy $topLocationKey")
class ActiveLocationCannotBePermanentlyDeactivatedException(key: String) : Exception("$key: Location cannot be permanently deactivated as it is active")
class LocationIsNotACellException(key: String) : Exception("$key: Location must be a cell in order to perform this operation")
class ApprovalRequestNotFoundException(id: String) : Exception("There is no approval request found $id")
class LockedLocationCannotBeUpdatedException(key: String) : Exception("Location $key cannot be updated as it is locked")
class PendingApprovalAlreadyExistsException(key: String) : Exception("Location $key already has a pending approval request")
