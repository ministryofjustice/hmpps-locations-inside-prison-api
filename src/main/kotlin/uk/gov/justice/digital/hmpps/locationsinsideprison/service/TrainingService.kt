package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.utils.AuthenticationFacade

@Service
class TrainingService(
  private val locationRepository: LocationRepository,
  private val authenticationFacade: AuthenticationFacade,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun setupTrainingPrisons() {
    val prisonIds = locationRepository.findDistinctListOfPrisonIds()

    prisonIds.forEach {
      locationRepository.setupTrainingPrison(prisonId = it, username = authenticationFacade.getUserOrSystemInContext())
      log.info("Reset training database for $it")
    }
  }
}
