package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository

@Service
class TrainingService(
  private val locationRepository: LocationRepository,
  private val linkedTransactionRepository: LinkedTransactionRepository,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun setupTrainingPrisons() {
    log.info("Reset training database : START")
    val prisonIds = locationRepository.findDistinctListOfPrisonIds()

    prisonIds.forEach {
      locationRepository.setupTrainingPrison(prisonId = it, username = "ZQW42M")
      log.info("Reset training database for $it")
    }
    linkedTransactionRepository.deleteAll()
    log.info("Reset training database : END")
  }
}
