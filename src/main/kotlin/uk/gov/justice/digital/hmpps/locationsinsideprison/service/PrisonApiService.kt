package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate

inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}

@Service
class PrisonApiService(
  private val prisonApiWebClient: WebClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getMovementTodayInAndOutOfPrison(prisonId: String): PrisonRollMovementInfo = prisonApiWebClient
    .get()
    .uri("/api/prison/roll-count/$prisonId/movement-count")
    .header("Content-Type", "application/json")
    .retrieve()
    .bodyToMono<PrisonRollMovementInfo>()
    .block()!!

  fun getOffenderMovementOutOfPrison(prisonId: String, date: LocalDate): List<OffenderMovement> = prisonApiWebClient
    .get()
    .uri("/api/movements/$prisonId/out/$date")
    .header("Content-Type", "application/json")
    .retrieve()
    .bodyToMono(typeReference<List<OffenderMovement>>())
    .block() ?: emptyList()
}

data class MovementCount(
  val `in`: Int,
  val out: Int,
)

data class PrisonRollMovementInfo(
  val inOutMovementsToday: MovementCount,
  val enRouteToday: Int,
)

data class OffenderMovement(
  val offenderNo: String,
  val movementSequence: String?,
  val movementType: String,
)
