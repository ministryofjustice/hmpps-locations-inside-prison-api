package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
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
    return mapper.convertValue(this.messageAttributes, UpdateFromExternalSystemDeactivateEvent::class.java).copy(updatedBy = who)
  }
}

data class UpdateFromExternalSystemDeactivateEvent(
  val id: UUID,
  val deactivationReason: DeactivatedReason,
  val deactivationReasonDescription: String? = null,
  val proposedReactivationDate: LocalDate? = null,
  val planetFmReference: String? = null,
  val updatedBy: String? = null,
)
