package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class PrisonerSearchService(
  private val prisonerSearchWebClient: WebClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  /**
   * Search locations for prisoners
   *
   * Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
   */
  fun findPrisonersInLocations(
    prisonId: String,
    locations: List<String>,
  ): Map<String, List<Prisoner>> {
    val searchTerm = locations.sorted().joinToString(",")
    val requestBody = AttributeSearch(
      queries = listOf(
        AttributeQuery(
          matchers = listOf(
            Matcher(attribute = "prisonId", condition = "IS", searchTerm = prisonId),
            Matcher(attribute = "cellLocation", condition = "IN", searchTerm = searchTerm),
          ),
        ),
      ),
    )

    log.info("Checking for prisoners in locations {}", searchTerm)

    val prisonersInLocations = prisonerSearchWebClient
      .post()
      .uri("/attribute-search?size=${locations.size}")
      .header("Content-Type", "application/json")
      .bodyValue(requestBody)
      .retrieve()
      .bodyToMono<SearchResult>()
      .block()!!
      .content.groupBy { it.cellLocation }

    return prisonersInLocations
  }
}

data class SearchResult(
  val content: List<Prisoner>,
)

data class Prisoner(
  val prisonerNumber: String,
  val firstName: String,
  val lastName: String,
  val prisonId: String?,
  val prisonName: String?,
  val cellLocation: String,
)

data class AttributeSearch(
  val joinType: String = "AND",
  val queries: List<AttributeQuery>,
)

data class AttributeQuery(
  val joinType: String = "AND",
  val matchers: List<Matcher>,
)

data class Matcher(
  val type: String = "String",
  val attribute: String,
  val condition: String,
  val searchTerm: String,
)
