package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.ValidationException
import jakarta.validation.constraints.Size
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonNotificationMailbox
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.TransactionType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonNotificationMailboxRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotificationMailboxNotFoundException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PrisonNotificationMailboxService(
  private val prisonNotificationMailboxRepository: PrisonNotificationMailboxRepository,
  private val activePrisonService: ActivePrisonService,
  private val linkedTransactionRepository: LinkedTransactionRepository,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val clock: Clock,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(PrisonNotificationMailboxService::class.java)

    // basic sanity check for "local-part@domain" shape; deliberately not exhaustive RFC 5322 validation
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }

  fun getMailboxes(prisonId: String, notificationGroup: NotificationGroup): PrisonNotificationMailboxDto {
    val mailboxes = prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, notificationGroup)
    if (mailboxes.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException(prisonId, notificationGroup)
    }
    return mailboxes.toDto(prisonId, notificationGroup)
  }

  @Transactional
  fun replaceMailboxes(prisonId: String, notificationGroup: NotificationGroup, emailAddresses: List<String>): PrisonNotificationMailboxDto {
    activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val normalisedEmails = emailAddresses.map { it.trim().lowercase() }.distinct()
    normalisedEmails.filterNot { EMAIL_REGEX.matches(it) }.also {
      if (it.isNotEmpty()) throw ValidationException("${it.size} email address(es) are not valid")
    }

    prisonNotificationMailboxRepository.deleteByPrisonIdAndNotificationGroup(prisonId, notificationGroup)

    val updatedBy = authenticationHolder.username ?: SYSTEM_USERNAME
    val now = LocalDateTime.now(clock)
    val saved = prisonNotificationMailboxRepository.saveAll(
      normalisedEmails.map { email ->
        PrisonNotificationMailbox(
          prisonId = prisonId,
          notificationGroup = notificationGroup,
          emailAddress = email,
          whenUpdated = now,
          updatedBy = updatedBy,
        )
      },
    )

    val tx = LinkedTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_UPDATE,
      prisonId = prisonId,
      // NB: never include the actual email addresses here - this is PII and must not appear in audit/telemetry/logs
      transactionDetail = "Notification mailbox updated for group $notificationGroup (${normalisedEmails.size} address(es))",
      transactionInvokedBy = updatedBy,
      txStartTime = now,
      txEndTime = now,
    )
    linkedTransactionRepository.save(tx)

    telemetryClient.trackEvent(
      "Notification mailbox update",
      mapOf(
        "prisonId" to prisonId,
        "notificationGroup" to notificationGroup.name,
        "count" to normalisedEmails.size.toString(),
        "tx" to tx.transactionId.toString(),
      ),
      null,
    )
    log.info("Updated notification mailbox for prisonId=$prisonId, notificationGroup=$notificationGroup, count=${normalisedEmails.size} [$tx]")

    return saved.toDto(prisonId, notificationGroup)
  }

  @Transactional
  fun deleteMailboxes(prisonId: String, notificationGroup: NotificationGroup) {
    val existing = prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, notificationGroup)
    if (existing.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException(prisonId, notificationGroup)
    }

    prisonNotificationMailboxRepository.deleteAll(existing)

    val updatedBy = authenticationHolder.username ?: SYSTEM_USERNAME
    val now = LocalDateTime.now(clock)
    val tx = LinkedTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_DELETE,
      prisonId = prisonId,
      transactionDetail = "Notification mailbox deleted for group $notificationGroup (${existing.size} address(es))",
      transactionInvokedBy = updatedBy,
      txStartTime = now,
      txEndTime = now,
    )
    linkedTransactionRepository.save(tx)

    telemetryClient.trackEvent(
      "Notification mailbox delete",
      mapOf(
        "prisonId" to prisonId,
        "notificationGroup" to notificationGroup.name,
        "count" to existing.size.toString(),
        "tx" to tx.transactionId.toString(),
      ),
      null,
    )
    log.info("Deleted notification mailbox for prisonId=$prisonId, notificationGroup=$notificationGroup, count=${existing.size} [$tx]")
  }

  private fun List<PrisonNotificationMailbox>.toDto(prisonId: String, notificationGroup: NotificationGroup) = PrisonNotificationMailboxDto(
    prisonId = prisonId,
    notificationGroup = notificationGroup,
    emailAddresses = map { it.emailAddress }.sorted(),
  )
}

@Schema(description = "Prison notification mailbox")
data class PrisonNotificationMailboxDto(
  @param:Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @param:Schema(description = "Notification group", example = "CERT_ADMIN", required = true)
  val notificationGroup: NotificationGroup,
  @param:Schema(description = "Email addresses registered for this prison and notification group", required = true)
  val emailAddresses: List<String>,
)

@Schema(description = "Request to replace all email addresses for a prison notification mailbox")
data class UpdateNotificationMailboxRequest(
  @param:Schema(description = "Email addresses to store, replacing any existing addresses for this prison and notification group", required = true)
  @field:Size(min = 1, message = "At least one email address is required")
  val emailAddresses: List<String>,
)

enum class NotificationGroup {
  CERT_ADMIN,
  CERT_VIEWER,
  CERT_REVIEWER,
}
