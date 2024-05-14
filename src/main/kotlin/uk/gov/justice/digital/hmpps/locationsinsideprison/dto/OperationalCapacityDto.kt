package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity as OperationalCapacityJPA

@Schema(description = "Operational Capacity Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OperationalCapacity(

  @Schema(description = "Operational Capacity Id", example = "10001", required = false)
  val id: Long? = null,

  @Schema(description = "Operational Capacity", example = "100", required = true)
  val capacity: Int,

  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,

  @Schema(description = "Data Time stamp", example = "2024-11-11", required = true)
  val dateTime: LocalDateTime,

  @Schema(description = "Approved by", example = "MALEMAN", required = true)
  val approvedBy: String,

)

interface CreateOperationalRequest {
  val id: Long?
  val capacity: Int
  val prisonId: String
  val dateTime: LocalDateTime
  val approvedBy: String
}

/**
 * Request format to create an Operational Capacity
 */
@Schema(description = "Request to create a Operational Capacity")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateOperationalCapacityRequest(

  override val id: Long?,

  @Schema(description = "Capacity of the residential Prison Id", required = false)
  override val capacity: Int,

  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  override val prisonId: String,

  @Schema(description = "Data Time stamp", example = "2024-11-11", required = true)
  override val dateTime: LocalDateTime,

  @Schema(description = "Approved by", example = "MALEMAN", required = true)
  @field:Size(max = 80, message = "Description must be less than 81 characters")
  override val approvedBy: String,

  ) : CreateOperationalRequest {

  fun toNewEntity(id:Long?, prisonId: String, capacity: Int, dateTime: LocalDateTime, approvedBy: String): OperationalCapacityJPA {
      val ope = OperationalCapacityJPA(
        id = id,
        prisonId = prisonId,
        capacity = capacity,
        approvedBy = approvedBy,
        dateTime = dateTime)
      return ope
    }
}