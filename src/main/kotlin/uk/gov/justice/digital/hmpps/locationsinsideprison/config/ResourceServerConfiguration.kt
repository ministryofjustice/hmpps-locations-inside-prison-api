package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import uk.gov.justice.hmpps.kotlin.auth.HmppsResourceServerConfiguration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {

  @Bean
  fun securityFilterChain(
    http: HttpSecurity,
    resourceServerCustomizer: ResourceServerConfigurationCustomizer,
  ): SecurityFilterChain = HmppsResourceServerConfiguration().hmppsSecurityFilterChain(http, resourceServerCustomizer)

  @Bean("resourceServerCustomizer")
  @Profile("!train")
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer {
  }

  @Bean("resourceServerCustomizer")
  @Profile("train")
  fun resourceServerCustomizerTrain() = ResourceServerConfigurationCustomizer {
    unauthorizedRequestPaths {
      addPaths = setOf("/reset-training")
    }
  }
}
