package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfiguration(
  @param:Value($$"${spring.jackson.time-zone}") private val timeZone: String,
) {
  @Bean
  fun clock(): Clock = Clock.system(ZoneId.of(timeZone))
}
