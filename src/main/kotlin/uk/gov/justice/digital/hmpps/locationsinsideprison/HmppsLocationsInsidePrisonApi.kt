package uk.gov.justice.digital.hmpps.locationsinsideprison

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

const val SYSTEM_USERNAME = "LOCATIONS_INSIDE_PRISON_API"

@SpringBootApplication
class HmppsLocationsInsidePrisonApi

fun main(args: Array<String>) {
  runApplication<HmppsLocationsInsidePrisonApi>(*args)
}
