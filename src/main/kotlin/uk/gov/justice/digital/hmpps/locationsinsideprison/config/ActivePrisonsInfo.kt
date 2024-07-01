package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component

@Component
class ActivePrisonsInfo(
  private val activePrisonConfig: ActivePrisonConfig,
) : InfoContributor {
  override fun contribute(builder: Info.Builder?) {
    builder?.withDetail("activeAgencies", activePrisonConfig.activePrisons)
  }
}
