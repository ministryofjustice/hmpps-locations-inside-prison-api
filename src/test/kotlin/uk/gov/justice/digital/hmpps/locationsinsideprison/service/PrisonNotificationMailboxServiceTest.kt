package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonNotificationMailbox
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonNotificationMailboxRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotFoundException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PrisonNotificationMailboxNotFoundException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.LocalDateTime

class PrisonNotificationMailboxServiceTest {
  private val prisonNotificationMailboxRepository: PrisonNotificationMailboxRepository = mock()
  private val activePrisonService: ActivePrisonService = mock()
  private val linkedTransactionRepository: LinkedTransactionRepository = mock()
  private val authenticationHolder: HmppsAuthenticationHolder = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val service = PrisonNotificationMailboxService(
    prisonNotificationMailboxRepository,
    activePrisonService,
    linkedTransactionRepository,
    authenticationHolder,
    clock,
    telemetryClient,
  )

  private val prisonId = "MDI"
  private val secretEmail = "governor.secret.address@justice.gov.uk"

  @BeforeEach
  fun setUp() {
    whenever(authenticationHolder.username).thenReturn("TEST_USER")
    whenever(linkedTransactionRepository.save(any<LinkedTransaction>())).thenReturn(Mockito.mock())
    whenever(prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(any())).thenReturn(emptyList())
    whenever(activePrisonService.getPrisonConfiguration(any())).thenReturn(
      PrisonConfiguration(id = prisonId, whenUpdated = LocalDateTime.now(clock), updatedBy = "TEST_USER"),
    )
  }

  @Test
  fun `throws not found when no mailboxes exist for prison and group`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_ADMIN)).thenReturn(emptyList())

    assertThatThrownBy { service.getMailboxes(prisonId, NotificationGroup.CERT_ADMIN) }
      .isInstanceOf(PrisonNotificationMailboxNotFoundException::class.java)
  }

  @Test
  fun `returns email addresses when mailboxes exist`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_ADMIN)).thenReturn(
      listOf(
        PrisonNotificationMailbox(prisonId = prisonId, notificationGroup = NotificationGroup.CERT_ADMIN, emailAddress = secretEmail, whenUpdated = LocalDateTime.now(clock), updatedBy = "TEST_USER"),
      ),
    )

    val result = service.getMailboxes(prisonId, NotificationGroup.CERT_ADMIN)

    assertThat(result.emailAddresses).containsExactly(secretEmail)
    assertThat(result.source).isEqualTo(NotificationMailboxSource.PRISON)
  }

  @Test
  fun `returns default email addresses when no prison mailboxes exist`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_VIEWER)).thenReturn(emptyList())
    whenever(prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(NotificationGroup.CERT_VIEWER)).thenReturn(
      listOf(
        PrisonNotificationMailbox(prisonId = null, notificationGroup = NotificationGroup.CERT_VIEWER, emailAddress = secretEmail, whenUpdated = LocalDateTime.now(clock), updatedBy = "TEST_USER"),
      ),
    )

    val result = service.getMailboxes(prisonId, NotificationGroup.CERT_VIEWER)

    assertThat(result.prisonId).isEqualTo(prisonId)
    assertThat(result.emailAddresses).containsExactly(secretEmail)
    assertThat(result.source).isEqualTo(NotificationMailboxSource.DEFAULT)
  }

  @Test
  fun `does not return default email addresses when includeDefault is false`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_VIEWER)).thenReturn(emptyList())

    assertThatThrownBy { service.getMailboxes(prisonId, NotificationGroup.CERT_VIEWER, includeDefault = false) }
      .isInstanceOf(PrisonNotificationMailboxNotFoundException::class.java)
  }

  @Test
  fun `replace fails when prison does not exist`() {
    whenever(activePrisonService.getPrisonConfiguration(any())).thenReturn(null)

    assertThatThrownBy { service.replaceMailboxes(prisonId, NotificationGroup.CERT_ADMIN, listOf(secretEmail)) }
      .isInstanceOf(PrisonNotFoundException::class.java)
  }

  @Test
  fun `replace normalises and dedupes email addresses then overwrites existing rows`() {
    whenever(prisonNotificationMailboxRepository.saveAll(any<List<PrisonNotificationMailbox>>())).thenAnswer { it.arguments[0] }

    val result = service.replaceMailboxes(
      prisonId,
      NotificationGroup.CERT_ADMIN,
      listOf(" $secretEmail ", secretEmail.uppercase()),
    )

    verify(prisonNotificationMailboxRepository).deleteByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_ADMIN)
    assertThat(result.emailAddresses).containsExactly(secretEmail)
  }

  @Test
  fun `replace default mailboxes stores rows without a prison id`() {
    whenever(prisonNotificationMailboxRepository.saveAll(any<List<PrisonNotificationMailbox>>())).thenAnswer { it.arguments[0] }

    val result = service.replaceDefaultMailboxes(NotificationGroup.CERT_VIEWER, listOf(secretEmail))

    verify(prisonNotificationMailboxRepository).deleteByPrisonIdIsNullAndNotificationGroup(NotificationGroup.CERT_VIEWER)
    assertThat(result.prisonId).isNull()
    assertThat(result.emailAddresses).containsExactly(secretEmail)
    assertThat(result.source).isEqualTo(NotificationMailboxSource.DEFAULT)
  }

  @Test
  fun `replace does not leak email addresses into linked transaction or telemetry`() {
    whenever(prisonNotificationMailboxRepository.saveAll(any<List<PrisonNotificationMailbox>>())).thenAnswer { it.arguments[0] }

    service.replaceMailboxes(prisonId, NotificationGroup.CERT_ADMIN, listOf(secretEmail))

    val txCaptor = argumentCaptor<LinkedTransaction>()
    verify(linkedTransactionRepository).save(txCaptor.capture())
    assertThat(txCaptor.firstValue.transactionDetail).doesNotContain(secretEmail)

    val propertiesCaptor = argumentCaptor<Map<String, String>>()
    verify(telemetryClient).trackEvent(any(), propertiesCaptor.capture(), anyOrNull())
    assertThat(propertiesCaptor.firstValue.values).noneMatch { it.contains(secretEmail) }
  }

  @Test
  fun `delete fails when no mailboxes exist`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_ADMIN)).thenReturn(emptyList())

    assertThatThrownBy { service.deleteMailboxes(prisonId, NotificationGroup.CERT_ADMIN) }
      .isInstanceOf(PrisonNotificationMailboxNotFoundException::class.java)
  }

  @Test
  fun `delete default fails with a default-specific not found message when no mailboxes exist`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdIsNullAndNotificationGroup(NotificationGroup.CERT_VIEWER)).thenReturn(emptyList())

    assertThatThrownBy { service.deleteDefaultMailboxes(NotificationGroup.CERT_VIEWER) }
      .isInstanceOf(PrisonNotificationMailboxNotFoundException::class.java)
      .hasMessage("There is no default notification mailbox found for notificationGroup = CERT_VIEWER")
  }

  @Test
  fun `delete removes existing mailboxes and records redacted linked transaction`() {
    whenever(prisonNotificationMailboxRepository.findByPrisonIdAndNotificationGroup(prisonId, NotificationGroup.CERT_ADMIN)).thenReturn(
      listOf(
        PrisonNotificationMailbox(prisonId = prisonId, notificationGroup = NotificationGroup.CERT_ADMIN, emailAddress = secretEmail, whenUpdated = LocalDateTime.now(clock), updatedBy = "TEST_USER"),
      ),
    )

    service.deleteMailboxes(prisonId, NotificationGroup.CERT_ADMIN)

    val txCaptor = argumentCaptor<LinkedTransaction>()
    verify(linkedTransactionRepository).save(txCaptor.capture())
    assertThat(txCaptor.firstValue.transactionDetail).doesNotContain(secretEmail)
  }
}
