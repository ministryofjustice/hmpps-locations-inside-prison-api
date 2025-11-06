package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
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

  fun isActivePrison(prisonId: String) = isAllPrisonsActive() || (getPrisonConfiguration(prisonId)?.resiLocationServiceActive ?: false)

  fun isCertificationApprovalRequired(prisonId: String) = getPrisonConfiguration(prisonId)?.certificationApprovalRequired == true

  fun getPrisonConfiguration(prisonId: String): PrisonConfiguration? = prisonConfigurationRepository.findById(prisonId).getOrNull()

  @Cacheable(CacheConfiguration.ACTIVE_PRISONS_CACHE_NAME)
  fun getActivePrisons(): List<String> = prisonConfigurationRepository.findAll().filter { it.resiLocationServiceActive }.map { it.id }

  @CacheEvict(CacheConfiguration.ACTIVE_PRISONS_CACHE_NAME)
  fun setResiLocationServiceActive(prisonId: String, resiLocationServiceActive: Boolean) {
    getPrisonConfiguration(prisonId)?.resiLocationServiceActive = resiLocationServiceActive
  }

  @CacheEvict(CacheConfiguration.ACTIVE_PRISONS_CACHE_NAME)
  fun setNonResiServiceActive(prisonId: String, nonResiServiceActive: Boolean) {
    getPrisonConfiguration(prisonId)?.nonResiServiceActive = nonResiServiceActive
  }
}
