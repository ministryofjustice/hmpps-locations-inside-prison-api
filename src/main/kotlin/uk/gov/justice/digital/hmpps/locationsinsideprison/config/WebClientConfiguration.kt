package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @param:Value($$"${api.base.url.oauth}") private val authBaseUri: String,
  @param:Value($$"${api.base.url.prisoner-search}") private val prisonerSearchUri: String,
  @param:Value($$"${api.base.url.prison-register}") private val prisonRegisterUri: String,
  @param:Value($$"${api.base.url.prison-api}") private val prisonApiUri: String,
  @param:Value($$"${api.base.url.manage-users}") private val manageUsersApiUri: String,
  @param:Value($$"${api.timeout:20s}") val healthTimeout: Duration,
) {

  @Bean
  fun authHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(authBaseUri, healthTimeout)

  @Bean
  fun prisonerSearchHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonerSearchUri, healthTimeout)

  @Bean
  fun prisonRegisterWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonRegisterUri, healthTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiUri, healthTimeout)

  @Bean
  fun prisonerSearchWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = SYSTEM_USERNAME, url = prisonerSearchUri, healthTimeout)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = SYSTEM_USERNAME, url = prisonApiUri, healthTimeout)

  @Bean
  fun manageUsersHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(manageUsersApiUri, healthTimeout)

  @Bean
  fun manageUsersWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = SYSTEM_USERNAME, url = manageUsersApiUri, healthTimeout)
}
