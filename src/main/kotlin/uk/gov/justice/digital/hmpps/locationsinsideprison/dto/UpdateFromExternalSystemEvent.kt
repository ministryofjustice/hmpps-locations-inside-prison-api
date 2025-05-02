package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

data class UpdateFromExternalSystemEvent(
  val messageId: String,
  val eventType: String,
  val description: String? = null,
  val messageAttributes: Map<String, Any?> = emptyMap(),
  val who: String? = null,
) {
  fun toTemporarilyDeactivateLocationByKeyRequest(): TemporarilyDeactivateLocationByKeyRequest {
    val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).registerModule(JavaTimeModule())
    return mapper.convertValue(this.messageAttributes, TemporarilyDeactivateLocationByKeyRequest::class.java)
  }
}