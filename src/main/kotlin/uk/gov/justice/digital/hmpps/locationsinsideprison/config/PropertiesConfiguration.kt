package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
open class PropertiesConfiguration(
  @Value("classpath:whereabouts/patterns/*.properties") private val resources: Array<Resource>,
) {

  @Bean
  @Qualifier("whereaboutsGroups")
  open fun pfb(): PropertiesFactoryBean {
    val pfb = PropertiesFactoryBean()
    pfb.setLocations(*resources)
    return pfb
  }
}
