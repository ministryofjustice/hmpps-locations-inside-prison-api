package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class PrisonerSearchService(
  private val prisonerSearchWebClient: WebClient,
  objectMapper: ObjectMapper,
) {
  private val responseFields by lazy {
    objectMapper.serializerProviderInstance.findValueSerializer(Prisoner::class.java).properties()
      .asSequence()
      .joinToString(",") { it.name }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisonersInPrison(prisonId: String, pageSize: Int? = 3000): List<Prisoner> {
    val prisonersInPrison = prisonerSearchWebClient
      .get()
      .uri(
        "/prisoner-search/prison/$prisonId?size=$pageSize&responseFields={responseFields}",
        mapOf("responseFields" to responseFields),
      )
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
      .uri(
        "/attribute-search?size=$pageSize&responseFields={responseFields}",
        mapOf("responseFields" to responseFields),
      )
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
  @param:Schema(description = "Prisoner Information", example = "A1234AA", required = true)
  val prisonerNumber: String,
  @param:Schema(description = "Prison ID", example = "LEI", required = false)
  val prisonId: String?,
  @param:Schema(description = "Prison Name", example = "HMP Leeds", required = false)
  val prisonName: String?,
  @param:Schema(description = "Cell location of the prisoner", example = "1-1-001", required = false)
  val cellLocation: String? = null,
  @param:Schema(description = "Prisoner first name", example = "Dave", required = true)
  val firstName: String,
  @param:Schema(description = "Prisoner last name", example = "Jones", required = true)
  val lastName: String,
  @param:Schema(description = "Prisoner gender", example = "Male", required = true)
  val gender: String,
  @param:Schema(description = "Status of the prisoner", example = "ACTIVE IN", required = true)
  val status: String,
  @param:Schema(description = "In/Out status", example = "IN", required = true)
  val inOutStatus: String,
  @param:Schema(description = "Prisoner CSRA", example = "High", required = false)
  val csra: String? = null,
  @param:Schema(description = "Prisoner category", example = "C", required = false)
  val category: String? = null,
  @param:Schema(description = "Prisoner alerts", required = false)
  val alerts: List<Alert>? = null,
  @param:Schema(description = "Last Movement Type Code of prisoner", example = "CRT", required = false)
  val lastMovementTypeCode: String? = null,
)

data class Alert(
  @param:Schema(description = "Alert type", example = "X", required = true)
  val alertType: String,
  @param:Schema(description = "Alert code", example = "XA", required = true)
  val alertCode: String,
  @param:Schema(description = "Active alert", example = "true", required = true)
  val active: Boolean,
  @param:Schema(description = "Expired", example = "false", required = true)
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
