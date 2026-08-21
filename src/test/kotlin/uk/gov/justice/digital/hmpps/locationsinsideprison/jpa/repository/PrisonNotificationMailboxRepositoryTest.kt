package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonNotificationMailbox
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PrisonNotificationMailboxRepositoryTest : TestBase() {

  val testPrisonId = "MDI"
  val testUser = "USER"

  @Autowired
  lateinit var repository: PrisonNotificationMailboxRepository

  @BeforeEach
  fun setup() {
    repository.deleteAll()
  }

  private fun mailbox(email: String, group: NotificationGroup = NotificationGroup.CERT_ADMIN, prisonId: String = testPrisonId) = PrisonNotificationMailbox(
    prisonId = prisonId,
    notificationGroup = group,
    emailAddress = email,
    whenUpdated = LocalDateTime.now(clock),
    updatedBy = testUser,
  )

  @Test
  fun `returns empty list when no mailboxes exist for prison and group`() {
    val result = repository.findByPrisonIdAndNotificationGroup(testPrisonId, NotificationGroup.CERT_ADMIN)
    assertThat(result).isEmpty()
  }

  @Test
  fun `returns mailboxes only for the matching prison and group`() {
    repository.save(mailbox("cert.admin@justice.gov.uk", NotificationGroup.CERT_ADMIN))
    repository.save(mailbox("cert.viewer@justice.gov.uk", NotificationGroup.CERT_VIEWER))
    repository.save(mailbox("other.prison@justice.gov.uk", NotificationGroup.CERT_ADMIN, prisonId = "LEI"))

    val result = repository.findByPrisonIdAndNotificationGroup(testPrisonId, NotificationGroup.CERT_ADMIN)

    assertThat(result).hasSize(1)
    assertThat(result[0].emailAddress).isEqualTo("cert.admin@justice.gov.uk")
  }

  @Test
  fun `rejects duplicate email address for the same prison and group regardless of case`() {
    repository.saveAndFlush(mailbox("duplicate@justice.gov.uk"))

    assertThatThrownBy {
      repository.saveAndFlush(mailbox("DUPLICATE@justice.gov.uk"))
    }.isInstanceOf(DataIntegrityViolationException::class.java)
  }

  @Test
  fun `deletes all mailboxes for a prison and group`() {
    repository.save(mailbox("one@justice.gov.uk"))
    repository.save(mailbox("two@justice.gov.uk"))

    val deleted = repository.deleteByPrisonIdAndNotificationGroup(testPrisonId, NotificationGroup.CERT_ADMIN)

    assertThat(deleted).hasSize(2)
    assertThat(repository.findByPrisonIdAndNotificationGroup(testPrisonId, NotificationGroup.CERT_ADMIN)).isEmpty()
  }

  private fun assertThatThrownBy(block: () -> Unit) = org.assertj.core.api.Assertions.assertThatThrownBy(block)
}
