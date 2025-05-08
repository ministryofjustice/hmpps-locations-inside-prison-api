package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import java.time.Clock
import java.time.LocalDateTime

@Schema(description = "Request to create a wing")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateWingAndStructureRequest(
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,
  @Schema(description = "Code assigned to a wing", example = "B", required = true)
  @field:Size(max = 12, message = "Max of 12 characters")
  val wingCode: String,
  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Max of 80 characters")
  val wingDescription: String?,

  @Schema(description = "The structure of the wing", required = false)
  val wingStructure: List<ResidentialStructuralType> = listOf(
    ResidentialStructuralType.WING,
    ResidentialStructuralType.LANDING,
    ResidentialStructuralType.CELL,
  ),
) {

  fun toEntity(createdBy: String, clock: Clock, linkedTransaction: LinkedTransaction) = ResidentialLocation(
    prisonId = prisonId,
    code = wingCode,
    locationType = LocationType.valueOf(wingStructure.first().name),
    status = LocationStatus.DRAFT,
    pathHierarchy = wingCode,
    localName = wingDescription,
    createdBy = createdBy,
    whenCreated = LocalDateTime.now(clock),
    childLocations = mutableListOf(),
  ).apply {
    setStructure(wingStructure)
    addHistory(
      attributeName = LocationAttribute.LOCATION_CREATED,
      oldValue = null,
      newValue = getKey(),
      amendedBy = createdBy,
      amendedDate = LocalDateTime.now(clock),
      linkedTransaction = linkedTransaction,
    )
  }
}
