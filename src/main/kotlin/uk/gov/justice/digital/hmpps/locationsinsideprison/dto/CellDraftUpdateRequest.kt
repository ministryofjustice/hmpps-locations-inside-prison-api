package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.util.*

@Schema(description = "Request to a create location and cell locations below it")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CellDraftUpdateRequest(
  @param:Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,

  @param:Schema(
    description = "Parent location under which cells is to be updated, this location must be DRAFT",
    required = false,
  )
  val parentLocation: UUID,

  @param:Schema(description = "Used For Types for all cells", required = false)
  val cellsUsedFor: Set<UsedForType>? = null,

  @param:Schema(
    description = "Accommodation Type for all cells",
    required = false,
    defaultValue = "NORMAL_ACCOMMODATION",
    example = "NORMAL_ACCOMMODATION",
  )
  val accommodationType: AccommodationType = AccommodationType.NORMAL_ACCOMMODATION,

  @param:Schema(
    description = "Set of cells that are to be created or amended, if the location is for update then missing cells will be removed",
    required = true,
  )
  val cells: Set<CellInformation>,
)
