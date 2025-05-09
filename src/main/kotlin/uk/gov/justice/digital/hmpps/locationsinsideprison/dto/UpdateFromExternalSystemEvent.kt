package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.DeactivateLocationsRequest
import java.time.LocalDate
import java.util.UUID

data class UpdateFromExternalSystemEvent(
  val messageId: String,
  val eventType: String,
  val description: String? = null,
  val messageAttributes: Map<String, Any?> = emptyMap(),
  val who: String? = null,
) {
  fun toUpdateFromExternalSystemDeactivateEvent(): UpdateFromExternalSystemDeactivateEvent {
    val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).registerModule(JavaTimeModule())
    return mapper.convertValue(this.messageAttributes, UpdateFromExternalSystemDeactivateEvent::class.java)
  }
}

data class UpdateFromExternalSystemDeactivateEvent(
  val id: UUID,
  val deactivationReason: DeactivatedReason,
  val deactivationReasonDescription: String? = null,
  val proposedReactivationDate: LocalDate? = null,
  val planetFmReference: String? = null,
)