package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.CapacityChanges
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.InternalLocationDomainEventType
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import java.util.*

@RestController
@Validated
@RequestMapping("/locations/bulk", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Bulk Location Operations",
  description = "Performs bulk operations",
)
class BulkUpdateResource(
  private val locationService: LocationService,
) : EventBase() {

  @PutMapping("deactivate/temporary")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Bulk temporarily deactivate a location",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns deactivated locations",
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
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun bulkDeactivateLocations(
    @RequestBody @Validated deactivateLocationsRequest: DeactivateLocationsRequest,
  ): List<Location> = deactivate(locationService.deactivateLocations(deactivateLocationsRequest))

  @PutMapping("deactivate/permanent")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Bulk permanently deactivate a location, the location must already be temporarily deactivated",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns perm deactivated locations",
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
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun bulkPermanentlyDeactivateLocations(
    @RequestBody @Validated permanentDeactivationRequest: BulkPermanentDeactivationRequest,
  ): List<Location> = deactivate(mapOf(InternalLocationDomainEventType.LOCATION_DEACTIVATED to locationService.permanentlyDeactivateLocations(permanentDeactivationRequest)))

  @PutMapping("reactivate")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Re-activates a series of locations",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated locations",
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
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun bulkReactivateLocations(
    @RequestBody @Validated reactivateLocationsRequest: ReactivateLocationsRequest,
  ): List<Location> = reactivate(locationService.reactivateLocations(reactivateLocationsRequest))

  @PutMapping("capacity-update")
  @PreAuthorize("hasRole('ROLE_MAINTAIN_LOCATIONS') and hasAuthority('SCOPE_write')")
  @Operation(
    summary = "Update capacity in map of cell locations",
    description = "Requires role MAINTAIN_LOCATIONS and write scope",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns list of changes made to capacity locations",
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
        description = "Location not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun bulkUpdateCapacity(
    @RequestBody @Validated updateCapacityRequest: UpdateCapacityRequest,
  ): Map<String, List<CapacityChanges>> {
    with(locationService.updateCapacityOfCellLocations(updateCapacityRequest)) {
      update(updatedLocations)
      return audit
    }
  }
}

@Schema(description = "Update Capacities Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateCapacityRequest(
  @param:Schema(
    description = "List of capacities to update",
    example = "{\"TCI-A-1-001\": { \"maxCapacity\": 2, \"workingCapacity\": 1, \"certifiedNormalAccommodation\": 2 }, \"TCI-A-1-002\": { \"maxCapacity\": 3, \"workingCapacity\": 1, \"certifiedNormalAccommodation\": 1 } }",
  )
  val locations: Map<String, CellCapacityUpdateDetail>,
)

@Schema(description = "Bulk permanent deactivation request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BulkPermanentDeactivationRequest(
  @param:Schema(description = "Reason for permanent deactivation", example = "Wing demolished", required = true)
  @field:Size(max = 100, message = "Reason for permanent deactivation cannot be more than 100 characters")
  @field:NotBlank(message = "Reason for permanent deactivation cannot be blank")
  val reason: String,
  @param:Schema(
    description = "List of locations to permanently deactivate",
    required = true,
    example = "[ \"TCI-A-1-001\", \"TCI-B-1-001\", \"TCI-A-2-001\" ]",
  )
  @field:NotEmpty(message = "At least one location must be provided")
  val locations: List<String>,
)

@Schema(description = "Deactivate Locations Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeactivateLocationsRequest(
  @param:Schema(description = "List of locations to deactivate", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"deactivationReason\": \"DAMAGED\" } }")
  val locations: Map<UUID, TemporaryDeactivationLocationRequest>,
  @param:Schema(description = "The deactivation needs to be approved, if false (default) it will be classes a short term temporary deactivation", example = "false", required = false)
  val requiresApproval: Boolean = false,
  @param:Schema(description = "Username of the user requesting to deactivate the locations, if not provided the token username or client id will be used", example = "TESTUSER", required = false)
  @field:Size(max = 80, message = "The updatedBy field cannot be more than 80 characters")
  val updatedBy: String? = null,
)

@Schema(description = "Reactivate Locations Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReactivateLocationsRequest(
  @param:Schema(description = "List of locations to reactivate", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"cascadeReactivation\": false, \"capacity\": { \"workingCapacity\": 1, \"maxCapacity\": 2 } } }")
  val locations: Map<UUID, ReactivationDetail>,
)

@Schema(description = "Reactivation Details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReactivationDetail(
  @param:Schema(description = "List of locations to reactivate", defaultValue = "false", required = true, example = "true")
  val cascadeReactivation: Boolean = false,
  @param:Schema(description = "New capacity of the location, if null the old values are used", required = false, example = " { \"workingCapacity\": 1, \"maxCapacity\": 2 }")
  val capacity: Capacity? = null,
)

@Schema(description = "Bulk Update Cell Capacity Details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCapacityUpdateDetail(
  @param:Schema(description = "Max capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int,

  @param:Schema(description = "Working capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int,

  @param:Schema(description = "Indicates the capacity of the certified location (cell)", example = "1", required = false)
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  val certifiedNormalAccommodation: Int? = null,

  @param:Schema(description = "Working capacity of the location", example = "A1-03", required = false)
  @field:Size(max = 12, message = "Cell mark cannot be more than 12 characters")
  val cellMark: String? = null,

  @param:Schema(description = "Indicate that the cell as in-cell sanitation", example = "true", required = false)
  val inCellSanitation: Boolean? = null,
)
