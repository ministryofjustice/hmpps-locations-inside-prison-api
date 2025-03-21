package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.PropertiesFactoryBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class PropertiesConfiguration(
  @Value("classpath:locations/patterns/*.properties") private val resources: Array<Resource>,
) {

  @Bean
  @Qualifier("residentialGroups")
  fun generatePropertiesBean(): PropertiesFactoryBean {
    val properties = PropertiesFactoryBean()
    properties.setLocations(*resources)
    return properties
  }
}
