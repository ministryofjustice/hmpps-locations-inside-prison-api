package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateOrUpdateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialLocationDTO
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NonResidentialSummary
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.buildEventsToPublishOnUpdate
import java.util.*

@RestController
@Validated
@RequestMapping("/locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Non-residential functions",
  description = "Information in non-residential locations and CRUD operations on them",
)
class LocationNonResidentialResource(
  private val nonResidentialService: NonResidentialService,
) : EventBase() {

  @GetMapping("/non-residential/{id}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Returns non-residential information for this ID",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getNonResidentialLocation(
    @Schema(description = "The non-residential location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
  ) = nonResidentialService.getById(id = id)
    ?: throw LocationNotFoundException(id.toString())

  @PostMapping("/non-residential", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a non-residential location, not used by UI, data fixes only",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created location",
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
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createNonResidentialLocation(
    @RequestBody
    @Validated
    createNonResidentialLocationRequest: CreateNonResidentialLocationRequest,
  ): Location = eventPublishAndAudit(
    InternalLocationDomainEventType.LOCATION_CREATED,
  ) {
    nonResidentialService.createNonResidentialLocation(createNonResidentialLocationRequest)
  }

  @PostMapping("/non-residential/{prisonId}", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Creates a non-residential location in a specified prison with basic data",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created location",
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
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun createBasicNonResidentialLocation(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @RequestBody
    @Validated
    createRequest: CreateOrUpdateNonResidentialLocationRequest,
  ): NonResidentialLocationDTO = eventPublishNonResiAndAudit(
    InternalLocationDomainEventType.LOCATION_CREATED,
  ) {
    nonResidentialService.createBasicNonResidentialLocation(prisonId = prisonId, createRequest)
  }

  @PostMapping("/non-residential/prison/{prisonId}/generate-missing-children", produces = [MediaType.APPLICATION_JSON_VALUE])
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Generates missing child locations for services with parent",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Returns created locations",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun generateMissingChildren(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
  ): List<Location> = eventPublish {
    val createdLocations = nonResidentialService.createChildLocationsForServicesWithParent(prisonId)
    mapOf(InternalLocationDomainEventType.LOCATION_CREATED to createdLocations)
  }[InternalLocationDomainEventType.LOCATION_CREATED] ?: emptyList()

  @PutMapping("/non-residential/{id}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Update of a non-residential location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
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
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun updateNonResidentialLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody @Validated updateRequest: CreateOrUpdateNonResidentialLocationRequest,
  ): NonResidentialLocationDTO {
    val (location, events) = nonResidentialService.updateNonResidentialLocation(id, updateRequest)
    events.forEach { event ->
      eventPublishNonResiAndAudit(event) { location }
    }
    return location
  }

  @PatchMapping("/non-residential/{id}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Partial update of a non-residential location, not used by UI, data fixes only",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
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
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun patchNonResidentialLocation(
    @Schema(description = "The location Id", example = "de91dfa7-821f-4552-a427-bf2f32eafeb0", required = true)
    @PathVariable
    id: UUID,
    @RequestBody
    @Validated
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): Location {
    val results = nonResidentialService.updateNonResidentialLocation(id, patchLocationRequest)
    eventPublish(buildEventsToPublishOnUpdate(results))
    audit(id.toString()) { results.location.copy(childLocations = null, parentLocation = null) }
    return results.location
  }

  @PatchMapping("/non-residential/key/{key}")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Partial update of a non-residential location, not used by UI, data fixes only",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated location",
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
        description = "Missing required role. Requires the MAINTAIN_LOCATIONS role with write scope.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Location already exists",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun patchNonResidentialLocationByKey(
    @Schema(description = "The location key", example = "MDI-VISIT", required = true)
    @PathVariable
    key: String,
    @RequestBody
    @Validated
    patchLocationRequest: PatchNonResidentialLocationRequest,
  ): Location {
    val results = nonResidentialService.updateNonResidentialLocation(key, patchLocationRequest)
    eventPublish(buildEventsToPublishOnUpdate(results))
    audit(key) { results.location.copy(childLocations = null, parentLocation = null) }
    return results.location
  }

  @GetMapping("/non-residential/prison/{prisonId}/local-name/{localName}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Finds a non-residential location matching the local name for a given prison - returning ONLY the first one found",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns location matching local name",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Non residential location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun findLocationByLocalName(
    @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable prisonId: String,
    @Schema(description = "Alternative description to display for location", example = "Wing A", required = true)
    @Size(max = 40, message = "Description must be less than 41 characters")
    @PathVariable localName: String,
  ) = nonResidentialService.findByPrisonIdAndLocalName(prisonId = prisonId, localName = localName)

  @GetMapping("/non-residential/prison/{prisonId}/service/{serviceType}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return non-residential locations by service for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns non-residential locations",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getByPrisonAndServiceType(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Service", example = "HEARING_LOCATION", required = true)
    @PathVariable
    serviceType: ServiceType,
    @RequestParam(name = "sortByLocalName", required = false, defaultValue = "false") sortByLocalName: Boolean = false,
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "false") formatLocalName: Boolean = false,
    @RequestParam(name = "filterParents", required = false, defaultValue = "true") filterParents: Boolean = true,
  ): List<Location> = nonResidentialService.getByPrisonAndServiceType(
    prisonId = prisonId,
    serviceType = serviceType,
    sortByLocalName = sortByLocalName,
    formatLocalName = formatLocalName,
    filterParents = filterParents,
  )

  @GetMapping("/prison/{prisonId}/non-residential-usage-type")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Return non-residential locations by usage for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns non-residential locations",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getLocationsByPrisonWithUsageTypes(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @RequestParam(name = "sortByLocalName", required = false, defaultValue = "false") sortByLocalName: Boolean = false,
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "false") formatLocalName: Boolean = false,
    @RequestParam(name = "filterParents", required = false, defaultValue = "true") filterParents: Boolean = true,
  ): List<Location> = nonResidentialService.getByPrisonAndUsageType(prisonId = prisonId, sortByLocalName = sortByLocalName, formatLocalName = formatLocalName, filterParents = filterParents)

  @GetMapping("/prison/{prisonId}/non-residential-usage-type/{usageType}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Deprecated("Use /non-residential/prison/{prisonId}/service/{serviceType} instead")
  @Operation(
    summary = "Return non-residential locations by usage for this prison",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns non-residential locations",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getLocationsByPrisonAndNonResidentialUsageType(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Usage type", example = "APPOINTMENTS", required = true)
    @PathVariable
    usageType: NonResidentialUsageType,
    @RequestParam(name = "sortByLocalName", required = false, defaultValue = "false") sortByLocalName: Boolean = false,
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "false") formatLocalName: Boolean = false,
    @RequestParam(name = "filterParents", required = false, defaultValue = "true") filterParents: Boolean = true,
  ): List<Location> = nonResidentialService.getByPrisonAndUsageType(
    prisonId = prisonId,
    usageType = usageType,
    sortByLocalName = sortByLocalName,
    formatLocalName = formatLocalName,
    filterParents = filterParents,
  )

  @GetMapping("/prison/{prisonId}/non-residential")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Deprecated("Use /locations/non-residential/summary/{prisonId} instead")
  @Operation(
    summary = "Get a list of active non-residential locations for a prison (excluding RTU)",
    description = "Requires role VIEW_LOCATIONS",
    deprecated = true,
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns a list of non-residential locations for a prison",
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
        description = "Missing required role. Requires the VIEW_LOCATIONS role.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prison not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getNonResidentialLocationsForPrison(
    @Schema(
      description = "Prison Id",
      example = "MDI",
      required = true,
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @RequestParam(name = "sortByLocalName", required = false, defaultValue = "true") sortByLocalName: Boolean = true,
    @RequestParam(name = "formatLocalName", required = false, defaultValue = "true") formatLocalName: Boolean = true,
  ): List<Location> = nonResidentialService.getActiveNonResidentialLocationsForPrison(
    prisonId = prisonId,
    sortByLocalName = sortByLocalName,
    formatLocalName = formatLocalName,
  )

  @GetMapping("/non-residential/summary/{prisonId}")
  @PreAuthorize("hasRole('ROLE_VIEW_LOCATIONS')")
  @Operation(
    summary = "Get a paged list of non-residential locations for a prison (excluding RTU and BOXes)",
    description = "Requires role VIEW_LOCATIONS",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns a paged list of non-residential locations for a prison",
      ),
      ApiResponse(
        responseCode = "400",
        description = "When input parameters are not valid",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the VIEW_LOCATIONS role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getPaginatedNonResidentialList(
    @Schema(description = "Prison Id", example = "MDI", required = true, minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
    @Size(min = 3, message = "Prison ID must be a minimum of 3 characters")
    @NotBlank(message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID cannot be more than 5 characters")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters ending in an I or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Filter by given statuses", example = "[ACTIVE,INACTIVE]", required = false)
    @Parameter(
      description = "Filter by given statuses",
      example = "[ACTIVE,INACTIVE]",
      array = ArraySchema(
        schema = Schema(implementation = LocationStatus::class),
        arraySchema = Schema(
          requiredMode = Schema.RequiredMode.NOT_REQUIRED,
          nullable = true,
          defaultValue = "null",
        ),
      ),
    )
    @RequestParam(required = false)
    status: List<LocationStatus> = listOf(LocationStatus.ACTIVE, LocationStatus.INACTIVE),
    @Schema(description = "Filter by the local name, wildcarded and case insensitive", example = "Work", required = false)
    @Parameter(
      description = "Filter by the local name",
      example = "Work",
    )
    @RequestParam(required = false)
    localName: String? = null,
    @Schema(description = "Filter by given types", example = "[ADJUDICATION_ROOM,VIDEO_LINK]", required = false)
    @Parameter(
      description = "Filter by given types",
      example = "[ADJUDICATION_ROOM,VIDEO_LINK]",
      array = ArraySchema(
        schema = Schema(implementation = NonResidentialLocationType::class),
        arraySchema = Schema(
          requiredMode = Schema.RequiredMode.NOT_REQUIRED,
          nullable = true,
          defaultValue = "null",
        ),
      ),
    )
    @RequestParam(required = false)
    locationType: List<NonResidentialLocationType>? = null,
    @Schema(description = "Service Type", example = "APPOINTMENT", required = false)
    @Parameter(description = "Filter by service type", example = "APPOINTMENT", required = false)
    serviceType: ServiceType? = null,
    @ParameterObject
    @PageableDefault(page = 0, size = 100, sort = ["localName"], direction = Sort.Direction.ASC)
    pageable: Pageable,
  ): NonResidentialSummary = nonResidentialService.getNonResidentialLocationSummaryForPrison(
    prisonId = prisonId,
    serviceType = serviceType,
    pageable = pageable,
    statuses = status,
    locationTypes = locationType ?: emptyList(),
    searchByLocalName = localName,
  )
}
