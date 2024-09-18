package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
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

  fun getPrisonersInPrison(prisonId: String, pageSize: Int? = 3000): List<Prisoner> {
    val prisonersInPrison = prisonerSearchWebClient
      .get()
      .uri("/prisoner-search/prison/$prisonId?size=$pageSize")
      .header("Content-Type", "application/json")
      .retrieve()
      .bodyToMono<SearchResult>()
      .block()!!
      .content

    return prisonersInPrison
  }

  /**
   * Search locations for prisoners
   *
   * Requires ROLE_GLOBAL_SEARCH or ROLE_PRISONER_SEARCH role.
   */
  fun findPrisonersInLocations(
    prisonId: String,
    locations: List<String>,
    pageSize: Int? = 3000,
  ): List<Prisoner> {
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
      .uri("/attribute-search?size=$pageSize")
      .header("Content-Type", "application/json")
      .bodyValue(requestBody)
      .retrieve()
      .bodyToMono<SearchResult>()
      .block()!!
      .content

    return prisonersInLocations
  }
}

data class SearchResult(
  val content: List<Prisoner>,
)

@Schema(description = "Prisoner Information")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Prisoner(
  @Schema(description = "Prisoner Information", example = "A1234AA", required = true)
  val prisonerNumber: String,
  @Schema(description = "Prison ID", example = "LEI", required = false)
  val prisonId: String?,
  @Schema(description = "Prison Name", example = "HMP Leeds", required = false)
  val prisonName: String?,
  @Schema(description = "Cell location of the prisoner", example = "1-1-001", required = true)
  val cellLocation: String,
  @Schema(description = "Prisoner first name", example = "Dave", required = true)
  val firstName: String,
  @Schema(description = "Prisoner last name", example = "Jones", required = true)
  val lastName: String,
  @Schema(description = "Prisoner gender", example = "Male", required = true)
  val gender: String,
  @Schema(description = "Status of the prisoner", example = "ACTIVE IN", required = true)
  val status: String,
  @Schema(description = "In/Out status", example = "IN", required = true)
  val inOutStatus: String,
  @Schema(description = "Prisoner CSRA", example = "High", required = false)
  val csra: String? = null,
  @Schema(description = "Prisoner category", example = "C", required = false)
  val category: String? = null,
  @Schema(description = "Prisoner alerts", required = false)
  val alerts: List<Alert>? = null,
  @Schema(description = "Last Movement Type Code of prisoner", example = "CRT", required = false)
  val lastMovementTypeCode: String? = null,
)

data class Alert(
  @Schema(description = "Alert type", example = "X", required = true)
  val alertType: String,
  @Schema(description = "Alert code", example = "XA", required = true)
  val alertCode: String,
  @Schema(description = "Active alert", example = "true", required = true)
  val active: Boolean,
  @Schema(description = "Expired", example = "false", required = true)
  val expired: Boolean,
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
