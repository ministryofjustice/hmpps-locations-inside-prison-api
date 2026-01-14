package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ActivePrisonService

@Component
class ActivePrisonsInfo(
  private val activePrisonService: ActivePrisonService,
) : InfoContributor {
  override fun contribute(builder: Info.Builder) {
    builder.withDetail(
      "activeAgencies",
      if (activePrisonService.isAllPrisonsActive()) {
        listOf("***")
      } else {
        activePrisonService.getActivePrisons()
      },
    )
  }
}
