package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceFamilyType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import kotlin.collections.sortedBy

@RestController
@Validated
@RequestMapping("/constants", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Constants",
  description = "Returns location reference data.",
)
class LocationConstants(
  private val locationService: LocationService,
) : EventBase() {

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
  fun locationConstants(): Map<String, List<Constant>> = mapOf(
    "locationTypes" to LocationType.entries.map { Constant(it.name, it.description) }.sortedBy { it.description },
  )

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
  fun deactivatedReasonsConstants(): Map<String, List<Constant>> = mapOf(
    "deactivatedReasons" to DeactivatedReason.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

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
  fun locationAttributeConstants(): Map<String, List<Constant>> = mapOf(
    "residentialHousingTypes" to ResidentialHousingType.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

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
  fun nonResidentialUsageTypeConstants(): Map<String, List<Constant>> = mapOf(
    "nonResidentialUsageTypes" to NonResidentialUsageType.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

  @GetMapping("/service-types")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get service type reference data",
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
  fun nonResidentialServiceTypeConstants(): Map<String, List<Constant>> = mapOf(
    "nonResidentialServiceTypes" to ServiceType.entries.sortedBy { it.sequence }
      .map {
        Constant(
          key = it.name,
          description = it.description,
          attributes = mapOf(
            "serviceFamilyType" to it.serviceFamily,
            "serviceFamilyDescription" to it.serviceFamily.description,
          ),
        )
      },
  )

  @GetMapping("/service-family-types")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get service family type reference data",
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
  fun serviceFamilyAndTypes(): Map<String, List<CompoundConstant>> = mapOf(
    "serviceFamilyTypes" to ServiceFamilyType.entries.sortedBy { it.sequence }.map { it.toDto() },
  )

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
  fun residentialAttributeTypeConstants(): Map<String, List<CompoundConstant>> = mapOf(
    "residentialAttributeTypes" to ResidentialAttributeType.entries.map { attr ->
      val residentialAttributeTypeName = attr.name
      CompoundConstant(
        attr.name,
        attr.description,
        ResidentialAttributeValue.entries
          .filter { it.type.toString() == residentialAttributeTypeName }
          .map { Constant(it.name, it.description) }.sortedBy { it.description },
      )
    }.sortedBy { it.description },
  )

  @GetMapping("/accommodation-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get accommodation type data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns accommodation reference data",
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
  fun getAccommodationTypeConstants(): Map<String, List<Constant>> = mapOf(
    "accommodationTypes" to AccommodationType.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

  @GetMapping("specialist-cell-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get specialist cell type data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns specialist cell reference data",
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
  fun getSpecialistCellTypeConstants(): Map<String, List<Constant>> = mapOf(
    "specialistCellTypes" to SpecialistCellType.entries.sortedBy { it.sequence }.map {
      Constant(
        key = it.name,
        description = it.description,
        attributes = mapOf("affectsCapacity" to it.affectsCapacity),
        additionalInformation = it.additionalInformation,
      )
    },
  )

  @GetMapping("used-for-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get all used for types",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns used for type reference data",
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
  fun getUsedForTypeConstants(): Map<String, List<Constant>> = mapOf(
    "usedForTypes" to UsedForType.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

  @GetMapping("used-for-type/{prisonId}")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get used for type data for not female or secure estate",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns used for type reference data",
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
  @Validated
  @ResponseBody
  fun getUsedForTypeConstantsForSpecifiedPrison(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @PathVariable
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    prisonId: String,
  ): Map<String, List<Constant>> = mapOf(
    "usedForTypes" to locationService.getUsedForTypesForPrison(prisonId).sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

  @GetMapping("converted-cell-type")
  @PreAuthorize("hasRole('ROLE_READ_LOCATION_REFERENCE_DATA')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Get converted cell type data",
    description = "Requires the READ_LOCATION_REFERENCE_DATA role.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns converted cell reference data",
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
  fun getConvertedCellType(): Map<String, List<Constant>> = mapOf(
    "convertedCellTypes" to ConvertedCellType.entries.sortedBy { it.sequence }.map { Constant(it.name, it.description) },
  )

  @Schema(description = "Reference data information")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class Constant(
    @param:Schema(description = "Code of reference information", example = "ACCESSIBLE_CELL", required = true)
    val key: String,
    @param:Schema(description = "Description of reference code", example = "Accessible cell", required = true)
    val description: String,
    val attributes: Map<String, Any>? = null,
    @param:Schema(description = "Additional information about this reference code", example = "Some useful extra info", required = false)
    val additionalInformation: String? = null,
  )

  @Schema(description = "Reference data information")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  data class CompoundConstant(
    @param:Schema(description = "Code of reference information", example = "ACCESSIBLE_CELL", required = true)
    val key: String,
    @param:Schema(description = "Description of reference code", example = "Accessible cell", required = true)
    val description: String,
    @param:Schema(description = "Sub list of reference data values", required = true)
    val values: List<Constant>,
  )
}
