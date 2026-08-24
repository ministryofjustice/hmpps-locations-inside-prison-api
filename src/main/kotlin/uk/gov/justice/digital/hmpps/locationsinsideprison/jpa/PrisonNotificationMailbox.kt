package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import java.time.LocalDateTime
import java.util.UUID

@Entity
class PrisonNotificationMailbox(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  val prisonId: String? = null,

  @Enumerated(EnumType.STRING)
  val notificationGroup: NotificationGroup,

  val emailAddress: String,

  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as PrisonNotificationMailbox

    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0

  override fun toString(): String = "PrisonNotificationMailbox(prisonId='$prisonId', notificationGroup=$notificationGroup)"
}
