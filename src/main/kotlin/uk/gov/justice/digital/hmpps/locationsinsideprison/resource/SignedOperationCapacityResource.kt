package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityValidRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.SignedOperationCapacityService

@RestController
@Validated
@RequestMapping("/signed-op-cap", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Signed Operation Capacity",
  description = "Returns Signed Operation Capacity data per prison.",
)
class SignedOperationCapacityResource(
  private val signedOperationCapacityService: SignedOperationCapacityService,

) : EventBase() {

  @GetMapping("/{prisonId}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')") // todo Need create a New Role?
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get Signed Operation Capacity",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns Signed Operation Capacity data",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the ROLE_VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Signed operation capacity not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getSignedOperationCapacity(
    @Schema(
      description = "Prison Id",
      example = "MDI",
      required = true,
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @PathVariable
    prisonId: String,
  ): SignedOperationCapacityDto = signedOperationCapacityService.getSignedOperationalCapacity(prisonId)
    ?: throw SignedOperationCapacityNotFoundException(prisonId)

  @PostMapping("/")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Create or update the signed operation capacity",
    description = "Requires role ROLE_MAINTAIN_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created Signed Operation Capacity",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the ROLE_MAINTAIN_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "PrisonID not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Signed Operation Capacity already has this value",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateSignedOperationCapacity(
    @RequestBody
    @Validated
    signedOperationCapacityValidRequest: SignedOperationCapacityValidRequest,
  ): ResponseEntity<SignedOperationCapacityDto> {
    val saveSignedOperationalCapacity = signedOperationCapacityService.saveSignedOperationalCapacity(signedOperationCapacityValidRequest)
    val response = publishSignedOpCapChange {
      saveSignedOperationalCapacity.signedOperationCapacityDto
    }

    return ResponseEntity(
      response,
      if (saveSignedOperationalCapacity.newRecord) {
        HttpStatus.CREATED
      } else {
        HttpStatus.OK
      },
    )
  }
}
