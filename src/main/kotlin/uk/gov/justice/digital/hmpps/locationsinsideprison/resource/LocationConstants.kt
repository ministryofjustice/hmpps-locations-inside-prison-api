package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue

@RestController
@Validated
@RequestMapping("/constants", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Locations",
  description = "Returns location constants",
)
class LocationConstants() : EventBaseResource() {

  @GetMapping("/location-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get location reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @ResponseBody
  fun locationConstants(): Map<String, List<Constant>> {
    return mapOf(
      "locationTypes" to LocationType.entries.map { Constant(it.name, it.description) },
    )
  }

  @GetMapping("/deactivated-reason")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get deactivated reason reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @ResponseBody
  fun deactivedReasonsConstants(): Map<String, List<Constant>> {
    return mapOf(
      "deactivatedReasons" to DeactivatedReason.entries.map { Constant(it.name, it.description) },
    )
  }

  @GetMapping("/residential-housing-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get residential housing reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @ResponseBody
  fun locationAttributeConstants(): Map<String, List<Constant>> {
    return mapOf(
      "residentialHousingTypes" to ResidentialHousingType.entries.map { Constant(it.name, it.description) },
    )
  }

  @GetMapping("/non-residential-usage-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get non-residential usage reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @ResponseBody
  fun nonResidentialUsageTypeConstants(): Map<String, List<Constant>> {
    return mapOf(
      "nonResidentialUsageTypes" to NonResidentialUsageType.entries.map { Constant(it.name, it.description) },
    )
  }

  @GetMapping("/residential-attribute-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get residential attribute reference data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns residential attribute reference data",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the READ_LOCATION_REFERENCE_DATA role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @ResponseBody
  fun residentialAttributeTypeConstants(): Map<String, List<CompoundConstant>> {
    return mapOf(
      "residentialAttributeTypes" to ResidentialAttributeType.entries.map {
        val residentialAttributeTypeName = it.name
        CompoundConstant(
          it.name,
          it.description,
          ResidentialAttributeValue.entries
            .filter { it.type.toString() == residentialAttributeTypeName }
            .map { Constant(it.name, it.description) },
        )
      },
    )
  }

  data class Constant(val key: String, val description: String)
  data class CompoundConstant(val key: String, val description: String, val values: List<Constant>)
}
