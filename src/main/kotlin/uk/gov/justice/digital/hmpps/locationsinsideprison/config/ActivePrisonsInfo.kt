package uk.gov.justice.digital.hmpps.locationsinsideprison.config

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component

// The service is live in every prison. Per-prison residential activation is held in prison configuration, not here.
@Component
class ActivePrisonsInfo : InfoContributor {
  override fun contribute(builder: Info.Builder) {
    builder.withDetail("activeAgencies", listOf("***"))
  }
}
