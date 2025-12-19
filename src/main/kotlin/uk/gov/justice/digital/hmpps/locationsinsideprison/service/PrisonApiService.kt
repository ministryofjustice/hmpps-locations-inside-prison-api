package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}

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

  fun getMovementIntoPrison(
    prisonId: String,
    fromDate: LocalDateTime = LocalDate.now().atTime(LocalTime.MIN),
    toDate: LocalDateTime = LocalDate.now().atTime(LocalTime.MAX),
  ): List<Movement> = prisonApiWebClient
    .get()
    .uri(
      "api/movements/$prisonId/in?fromDateTime=" +
        "${fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}&toDateTime=" +
        "${toDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}",
    )
    .header("Content-Type", "application/json")
    .retrieve()
    .bodyToMono(typeReference<List<Movement>>())
    .block() ?: emptyList()

  fun getMovementOutOfPrison(prisonId: String, date: LocalDate): List<Movement> = prisonApiWebClient
    .get()
    .uri("/api/movements/$prisonId/out/$date")
    .header("Content-Type", "application/json")
    .retrieve()
    .bodyToMono(typeReference<List<Movement>>())
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

data class Movement(
  val offenderNo: String,
)
