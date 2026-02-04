package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.PostgresContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@ActiveProfiles("test")
abstract class TestBase {

  companion object {
    val clock: Clock = Clock.fixed(
      Instant.parse("2023-12-05T12:34:56.123456+00:00"),
      ZoneId.of("Europe/London"),
    )

    private val pgContainer = PostgresContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.run {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
        registry.add("spring.flyway.url", pgContainer::getJdbcUrl)
        registry.add("spring.flyway.user", pgContainer::getUsername)
        registry.add("spring.flyway.password", pgContainer::getPassword)
      }
    }
  }
}
