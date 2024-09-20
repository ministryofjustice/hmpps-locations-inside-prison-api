package uk.gov.justice.digital.hmpps.locationsinsideprison.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("prisonerSearchApi")
class PrisonerSearchApiHealth(@Qualifier("prisonerSearchHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("hmppsAuthApi")
class HmppsAuthApiHealth(@Qualifier("authHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonRegisterApi")
class PrisonRegisterApiHealth(@Qualifier("prisonRegisterWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonApi")
class PrisonApiHealth(@Qualifier("prisonApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)
