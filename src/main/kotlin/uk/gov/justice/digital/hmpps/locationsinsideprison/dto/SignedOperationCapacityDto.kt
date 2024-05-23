package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationCapacity as PrisonSignedOperationCapacityJPA

@Schema(description = "Signed Operation Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SignedOperationCapacityDto(

  @Schema(description = "Signed Operation Capacity", example = "100", required = true)
  val signedOperationCapacity: Int,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Data Time stamp", example = "2024-11-11", required = true)
  val dateTime: LocalDateTime,

  @Schema(description = "Updated by", example = "MALEMAN", required = true)
  val updatedBy: String,

)

interface CreateSignedOperationCapacityRequest {
  val signedOperationCapacity: Int
  val prisonId: String
  val dateTime: LocalDateTime
  val updatedBy: String
}

@Schema(description = "Request to create a Signed Operation Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSignedOperationCapacityValidRequest(

  @Schema(description = "signedOperationCapacity TODO", example = "1", required = false)
  override val signedOperationCapacity: Int,

  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  override val prisonId: String,

  @Schema(description = "TODO Type", example = "TODO", required = true)
  override val dateTime: LocalDateTime,

  @Schema(description = "Updated By", example = "TODO", required = true)
  @field:Size(max = 255, message = "TODO")
  override val updatedBy: String,

  ) : CreateSignedOperationCapacityRequest {
  fun toNewSignedOperationCapacity(
    signedOperationCapacity: Int,
    prisonId: String,
    dateTime: LocalDateTime,
    updatedBy: String
  ): PrisonSignedOperationCapacityJPA {
    val signedOperationCapacityVal = PrisonSignedOperationCapacityJPA(
      signedOperationCapacity = signedOperationCapacity,
      prisonId = prisonId,
      dateTime = LocalDateTime.now(),
      updatedBy = updatedBy,
    )

    return signedOperationCapacityVal
  }
}

