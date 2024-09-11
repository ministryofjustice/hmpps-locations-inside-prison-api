package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.PositiveOrZero
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
) : EventBaseResource() {

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
  ): List<Location> {
    return deactivate(locationService.deactivateLocations(deactivateLocationsRequest))
  }

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
  ): List<Location> {
    return reactivate(locationService.reactivateLocations(reactivateLocationsRequest))
  }

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
  @Schema(
    description = "List of capacities to update",
    example = "{\"TCI-A-1-001\": { \"maxCapacity\": 2, \"workingCapacity\": 1, \"capacityOfCertifiedCell\": 2 }, \"TCI-A-1-002\": { \"maxCapacity\": 3, \"workingCapacity\": 1, \"capacityOfCertifiedCell\": 1 } }")
  val locations: Map<String, CellCapacityUpdateDetail>,
)

@Schema(description = "Deactivation Locations Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeactivateLocationsRequest(
  @Schema(description = "List of locations to deactivate", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"deactivationReason\": \"DAMAGED\" } }")
  val locations: Map<UUID, TemporaryDeactivationLocationRequest>,
)

@Schema(description = "Reactivation Locations Request")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReactivateLocationsRequest(
  @Schema(description = "List of locations to reactivate", example = "{ \"de91dfa7-821f-4552-a427-bf2f32eafeb0\": { \"cascadeReactivation\": false, \"capacity\": { \"workingCapacity\": 1, \"maxCapacity\": 2 } } }")
  val locations: Map<UUID, ReactivationDetail>,
)

@Schema(description = "Reactivation Details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReactivationDetail(
  @Schema(description = "List of locations to reactivate", defaultValue = "false", required = true, example = "true")
  val cascadeReactivation: Boolean = false,
  @Schema(description = "New capacity of the location, if null the old values are used", required = false, example = " { \"workingCapacity\": 1, \"maxCapacity\": 2 }")
  val capacity: Capacity? = null,
)

@Schema(description = "Bulk Update Cell Capacity Details")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellCapacityUpdateDetail(
  @Schema(description = "Max capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Max capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Max capacity cannot be less than 0")
  val maxCapacity: Int,

  @Schema(description = "Working capacity of the location", example = "2", required = true)
  @field:Max(value = 99, message = "Working capacity cannot be greater than 99")
  @field:PositiveOrZero(message = "Working capacity cannot be less than 0")
  val workingCapacity: Int,

  @Schema(description = "Indicates the capacity of the certified location (cell)", example = "1", required = false)
  @field:Max(value = 99, message = "CNA cannot be greater than 99")
  @field:PositiveOrZero(message = "CNA cannot be less than 0")
  val capacityOfCertifiedCell: Int? = null,
)
