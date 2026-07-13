package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "Request to create a new property storage location. A top-level BOX location is created with a generated code and a PROPERTY usage carrying the capacity.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatePropertyLocationRequest(
  @param:Schema(description = "Name to display for the location", example = "Reception property store", required = true)
  @field:NotBlank(message = "Local name cannot be blank")
  @field:Size(min = 1, max = 80, message = "Local name must be between 1 and 80 characters")
  val localName: String,

  @param:Schema(description = "How many property containers this location can hold", example = "10", required = true)
  @field:NotNull(message = "Capacity must be provided")
  @field:Min(value = 0, message = "Capacity cannot be negative")
  val capacity: Int,
)

@Schema(description = "Request to update a property storage location's name and/or capacity. Omitted fields are left unchanged.")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdatePropertyLocationRequest(
  @param:Schema(description = "New name to display for the location", example = "Reception property store", required = false)
  @field:Size(min = 1, max = 80, message = "Local name must be between 1 and 80 characters")
  val localName: String? = null,

  @param:Schema(description = "New number of property containers this location can hold", example = "12", required = false)
  @field:Min(value = 0, message = "Capacity cannot be negative")
  val capacity: Int? = null,
)
