package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
@EnableScheduling
class CacheConfiguration {

  @Bean
  fun cacheManager(): CacheManager = ConcurrentMapCacheManager(ACTIVE_PRISONS_CACHE_NAME)

  @CacheEvict(value = [ACTIVE_PRISONS_CACHE_NAME], allEntries = true)
  @Scheduled(fixedDelay = TTL_ACTIVE_PRISONS, timeUnit = TimeUnit.HOURS)
  fun cacheEvictActivePrisons() {
    log.info("Evicting cache: $ACTIVE_PRISONS_CACHE_NAME after $TTL_ACTIVE_PRISONS hours")
  }

  companion object {
    val log: org.slf4j.Logger = LoggerFactory.getLogger(this::class.java)
    const val ACTIVE_PRISONS_CACHE_NAME: String = "activePrisons"
    const val TTL_ACTIVE_PRISONS: Long = 1
  }
}
