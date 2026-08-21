package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonNotificationMailbox
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import java.util.UUID

@Repository
interface PrisonNotificationMailboxRepository : JpaRepository<PrisonNotificationMailbox, UUID> {
  fun findByPrisonIdAndNotificationGroup(prisonId: String, notificationGroup: NotificationGroup): List<PrisonNotificationMailbox>

  fun deleteByPrisonIdAndNotificationGroup(prisonId: String, notificationGroup: NotificationGroup): List<PrisonNotificationMailbox>
}
