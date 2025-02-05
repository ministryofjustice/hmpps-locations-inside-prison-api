package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.CacheConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import kotlin.jvm.optionals.getOrNull

@Service
class ActivePrisonService(
  private val prisonConfigurationRepository: PrisonConfigurationRepository,
  @Value("\${service.residential.activated.per.prison:false}")
  val resiServiceActivatedPerPrison: Boolean = false,
) {
  fun isAllPrisonsActive() = !resiServiceActivatedPerPrison

  fun isActivePrison(prisonId: String) = isAllPrisonsActive() || (prisonConfigurationRepository.findById(prisonId).getOrNull()?.resiLocationServiceActive ?: false)

  @Cacheable(CacheConfiguration.ACTIVE_PRISONS_CACHE_NAME)
  fun getActivePrisons(): List<String> {
    return prisonConfigurationRepository.findAll().filter { it.resiLocationServiceActive }.map { it.prisonId }
  }
}
