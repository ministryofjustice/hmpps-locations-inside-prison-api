package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.config.CacheConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import kotlin.jvm.optionals.getOrNull

@Service
class ActivePrisonService(
  private val prisonConfigurationRepository: PrisonConfigurationRepository,
  @param:Value($$"${service.residential.activated.per.prison:false}")
  val resiServiceActivatedPerPrison: Boolean = false,
) {
  fun isAllPrisonsActive() = !resiServiceActivatedPerPrison

  // Non-residential locations are active in every prison, so any prison with a configuration is active
  fun isActivePrison(prisonId: String): Boolean = isAllPrisonsActive() || getPrisonConfiguration(prisonId) != null

  fun isCertificationApprovalRequired(prisonId: String) = getPrisonConfiguration(prisonId)?.certificationApprovalRequired == true

  fun getPrisonConfiguration(prisonId: String): PrisonConfiguration? = prisonConfigurationRepository.findById(prisonId).getOrNull()

  @Cacheable(CacheConfiguration.ACTIVE_PRISONS_CACHE_NAME)
  fun getActivePrisons(): List<String> = prisonConfigurationRepository.findAll().map { it.id }

  fun setResiLocationServiceActive(prisonId: String, resiLocationServiceActive: Boolean) {
    getPrisonConfiguration(prisonId)?.resiLocationServiceActive = resiLocationServiceActive
  }
}
