package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class PrisonService(
  private val prisonRegisterWebClient: WebClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * Lookup prison details
   */
  fun lookupPrisonDetails(prisonId: String): PrisonDto {
    log.debug("Looking up prison details {}", prisonId)

    val prison = prisonRegisterWebClient
      .get()
      .uri("/prisons/id/$prisonId")
      .header("Content-Type", "application/json")
      .retrieve()
      .bodyToMono<PrisonDto>()
      .block()!!

    return prison
  }
}

data class PrisonDto(
  val prisonId: String,
  val prisonName: String,
  val active: Boolean,
  val male: Boolean,
  val female: Boolean,
  val contracted: Boolean,
  val lthse: Boolean,
  val categories: Set<String> = setOf(),
)
