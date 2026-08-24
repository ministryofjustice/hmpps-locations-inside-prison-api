package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.fasterxml.jackson.annotation.JsonInclude
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
    private const val DEFAULT_MAILBOX_AUDIT_PRISON_ID = "N/A"

    // basic sanity check for "local-part@domain" shape; deliberately not exhaustive RFC 5322 validation
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }

  fun getMailboxes(prisonId: String, notificationGroup: NotificationGroup, includeDefault: Boolean = true): PrisonNotificationMailboxDto {
    val mailboxes = prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, notificationGroup)
    if (mailboxes.isNotEmpty()) {
      return mailboxes.toDto(prisonId, notificationGroup, NotificationMailboxSource.PRISON)
    }

    if (!includeDefault) {
      throw PrisonNotificationMailboxNotFoundException(prisonId, notificationGroup)
    }

    val defaultMailboxes = prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(notificationGroup)
    if (defaultMailboxes.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException(prisonId, notificationGroup)
    }
    return defaultMailboxes.toDto(prisonId, notificationGroup, NotificationMailboxSource.DEFAULT)
  }

  fun getDefaultMailboxes(notificationGroup: NotificationGroup): PrisonNotificationMailboxDto {
    val mailboxes = prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(notificationGroup)
    if (mailboxes.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException.default(notificationGroup)
    }
    return mailboxes.toDto(null, notificationGroup, NotificationMailboxSource.DEFAULT)
  }

  @Transactional
  fun replaceMailboxes(prisonId: String, notificationGroup: NotificationGroup, emailAddresses: List<String>): PrisonNotificationMailboxDto {
    activePrisonService.getPrisonConfiguration(prisonId) ?: throw PrisonNotFoundException(prisonId)

    val saved = replaceMailboxRows(prisonId, notificationGroup, emailAddresses)

    recordTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_UPDATE,
      prisonId = prisonId,
      notificationGroup = notificationGroup,
      count = saved.size,
      action = "updated",
    )

    return saved.toDto(prisonId, notificationGroup, NotificationMailboxSource.PRISON)
  }

  @Transactional
  fun replaceDefaultMailboxes(notificationGroup: NotificationGroup, emailAddresses: List<String>): PrisonNotificationMailboxDto {
    val saved = replaceMailboxRows(null, notificationGroup, emailAddresses)

    recordTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_UPDATE,
      prisonId = DEFAULT_MAILBOX_AUDIT_PRISON_ID,
      notificationGroup = notificationGroup,
      count = saved.size,
      action = "updated default",
    )

    return saved.toDto(null, notificationGroup, NotificationMailboxSource.DEFAULT)
  }

  private fun replaceMailboxRows(prisonId: String?, notificationGroup: NotificationGroup, emailAddresses: List<String>): List<PrisonNotificationMailbox> {
    val normalisedEmails = emailAddresses.map { it.trim().lowercase() }.distinct()
    normalisedEmails.filterNot { EMAIL_REGEX.matches(it) }.also {
      if (it.isNotEmpty()) throw ValidationException("${it.size} email address(es) are not valid")
    }

    if (prisonId == null) {
      prisonNotificationMailboxRepository.deleteByPrisonIdIsNullAndNotificationGroup(notificationGroup)
    } else {
      prisonNotificationMailboxRepository.deleteByPrisonIdAndNotificationGroup(prisonId, notificationGroup)
    }
    // Hibernate flushes inserts before deletes regardless of call order, so without this the re-added rows can collide with the not-yet-deleted old ones
    prisonNotificationMailboxRepository.flush()

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

    return saved.toList()
  }

  private fun recordTransaction(transactionType: TransactionType, prisonId: String, notificationGroup: NotificationGroup, count: Int, action: String) {
    val updatedBy = authenticationHolder.username ?: SYSTEM_USERNAME
    val now = LocalDateTime.now(clock)
    val tx = LinkedTransaction(
      transactionType = transactionType,
      prisonId = prisonId,
      // NB: never include the actual email addresses here - this is PII and must not appear in audit/telemetry/logs
      transactionDetail = "Notification mailbox $action for group $notificationGroup ($count address(es))",
      transactionInvokedBy = updatedBy,
      txStartTime = now,
      txEndTime = now,
    )
    linkedTransactionRepository.save(tx)

    telemetryClient.trackEvent(
      "Notification mailbox $action",
      mapOf(
        "prisonId" to prisonId,
        "notificationGroup" to notificationGroup.name,
        "count" to count.toString(),
        "tx" to tx.transactionId.toString(),
      ),
      null,
    )
    log.info("Notification mailbox $action for prisonId=$prisonId, notificationGroup=$notificationGroup, count=$count [$tx]")
  }

  @Transactional
  fun deleteMailboxes(prisonId: String, notificationGroup: NotificationGroup) {
    val existing = prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, notificationGroup)
    if (existing.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException(prisonId, notificationGroup)
    }

    prisonNotificationMailboxRepository.deleteAll(existing)

    recordTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_DELETE,
      prisonId = prisonId,
      notificationGroup = notificationGroup,
      count = existing.size,
      action = "deleted",
    )
  }

  @Transactional
  fun deleteDefaultMailboxes(notificationGroup: NotificationGroup) {
    val existing = prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(notificationGroup)
    if (existing.isEmpty()) {
      throw PrisonNotificationMailboxNotFoundException.default(notificationGroup)
    }

    prisonNotificationMailboxRepository.deleteAll(existing)

    recordTransaction(
      transactionType = TransactionType.NOTIFICATION_MAILBOX_DELETE,
      prisonId = DEFAULT_MAILBOX_AUDIT_PRISON_ID,
      notificationGroup = notificationGroup,
      count = existing.size,
      action = "deleted default",
    )
  }

  private fun List<PrisonNotificationMailbox>.toDto(prisonId: String?, notificationGroup: NotificationGroup, source: NotificationMailboxSource) = PrisonNotificationMailboxDto(
    prisonId = prisonId,
    notificationGroup = notificationGroup,
    emailAddresses = map { it.emailAddress }.sorted(),
    source = source,
  )
}

@Schema(description = "Prison notification mailbox")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PrisonNotificationMailboxDto(
  @param:Schema(description = "Prison ID, omitted for default notification mailboxes", example = "MDI", required = false)
  val prisonId: String?,
  @param:Schema(description = "Notification group", example = "CERT_ADMIN", required = true)
  val notificationGroup: NotificationGroup,
  @param:Schema(description = "Email addresses registered for this prison and notification group", required = true)
  val emailAddresses: List<String>,
  @param:Schema(description = "Indicates whether the response came from a prison-specific or default mailbox", example = "PRISON", required = true)
  val source: NotificationMailboxSource,
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

enum class NotificationMailboxSource {
  PRISON,
  DEFAULT,
}
