package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Configuration
class CustomisedJacksonObjectMapper(
  @Value("\${spring.jackson.time-zone}") private val timeZone: String,
  @Value("\${spring.jackson.date-format}") private val zonedDateTimeFormat: String,
) {
  @Bean
  fun serialiser() = Jackson2ObjectMapperBuilderCustomizer {
    val zoneId = ZoneId.of(timeZone)
    val naiveDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zoneId)
    val naiveDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(zoneId)
    val zonedDateTimeFormatter = DateTimeFormatter.ofPattern(zonedDateTimeFormat).withZone(zoneId)

    it.serializers(
      LocalDateSerializer(naiveDateFormatter),
      LocalDateTimeSerializer(naiveDateTimeFormatter),
      ZonedDateTimeSerializer(zonedDateTimeFormatter),
    )
  }
}
