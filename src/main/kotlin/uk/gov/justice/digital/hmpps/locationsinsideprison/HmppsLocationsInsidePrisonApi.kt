package uk.gov.justice.digital.hmpps.locationsinsideprison

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HmppsLocationsInsidePrisonApi

fun main(args: Array<String>) {
  runApplication<HmppsLocationsInsidePrisonApi>(*args)
}
