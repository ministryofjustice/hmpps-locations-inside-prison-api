package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import jakarta.validation.ValidationException
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(LocationCannotBeResidentialException::class)
  fun handleLocationAccomodationTypeOtherNonResidential(e: LocationCannotBeResidentialException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location acommodation type other non residential exception caught: {}", e.message)
    return ResponseEntity
      .status(HttpStatus.CONFLICT)
      .body(
        ErrorResponse(
          status = HttpStatus.CONFLICT,
          errorCode = ErrorCode.LocationAlreadyExists,
          userMessage = "Location acommodation type other non residential : ${e.message}",
          developerMessage = e.message,
        ),
      )
  }

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
      .status(HttpStatus.CONFLICT)
      .body(
        ErrorResponse(
          status = HttpStatus.CONFLICT,
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

  @ExceptionHandler(LocationAlreadyDeactivatedException::class)
  fun handleLocationAlreadyDeactivated(e: LocationAlreadyDeactivatedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Location already deactivated: {}", e.message)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          errorCode = ErrorCode.LocationAlreadyDeactivated,
          userMessage = "Location already deactivated: ${e.message}",
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

  @ExceptionHandler(ConvertedCellUpdateNotAllowedException::class)
  fun handleConvertedCellUpdateNotAllowedException(e: ConvertedCellUpdateNotAllowedException): ResponseEntity<ErrorResponse?>? {
    log.debug("Converted Cell Exception: {}", e.message)
    return ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          errorCode = ErrorCode.ConvertedCellLocationCannotByUpdated,
          userMessage = "Converted Cell Exception: ${e.message}",
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

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class LocationNotFoundException(id: String) : Exception("There is no location found for ID = $id")

class LocationPrefixNotFoundException(id: String) : Exception("Location prefix not found for $id")

class SignedOperationCapacityNotFoundException(prisonId: String) :
  Exception("There is no signed operation capacity found for prison ID = $prisonId")

class LocationAlreadyExistsException(key: String) : Exception("Location already exists = $key")
class LocationCannotBeReactivatedException(key: String) : Exception("Location cannot be reactivated if parent is deactivated = $key")
class LocationAlreadyDeactivatedException(key: String) : ValidationException("$key is already deactivated")
class CapacityException(val key: String, override val message: String, val errorCode: ErrorCode) : ValidationException("$key: [Error Code: $errorCode] - Capacity Exception: $message")
class PermanentlyDeactivatedUpdateNotAllowedException(key: String) : ValidationException("Location $key cannot be updated as permanently deactivated")
class ConvertedCellUpdateNotAllowedException(key: String) : Exception("Location $key cannot be updated as converted cell")
class LocationContainsPrisonersException(locationsWithPrisoners: Map<String, List<Prisoner>>) : Exception("${locationsWithPrisoners.keys.size} locations contain ${locationsWithPrisoners.values.size} prisoners")
class AlreadyDeactivatedLocationException(key: String) : ValidationException("$key: Cannot deactivate an already deactivated location")
class LocationCannotBeResidentialException(key: String) : Exception("Location AccommodationType $key cannot be converted to residential")
