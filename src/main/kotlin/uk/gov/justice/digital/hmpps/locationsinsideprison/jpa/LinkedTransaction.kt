package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TransactionHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.*

@Entity
class LinkedTransaction(

  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val transactionId: UUID? = null,

  @Enumerated(EnumType.STRING)
  val transactionType: TransactionType,

  val transactionDetail: String,
  val transactionInvokedBy: String,
  val txStartTime: LocalDateTime,
  var txEndTime: LocalDateTime? = null,

  @OneToMany(mappedBy = "linkedTransaction", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  val auditChanges: MutableList<LocationHistory> = mutableListOf(),
) {

  fun toDto(filterLocation: Location? = null) =
    TransactionHistory(
      transactionId = transactionId!!,
      transactionType = transactionType,
      transactionDetail = transactionDetail,
      transactionInvokedBy = transactionInvokedBy,
      txStartTime = txStartTime,
      txEndTime = txEndTime,
      changesToLocation = auditChanges
        .filter { audit -> (filterLocation == null || audit.location == filterLocation) && audit.attributeName.display }
        .sortedBy { it.amendedDate }.sortedBy { it.id }
        .map { it.toDto() },
    )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LinkedTransaction

    if (transactionType != other.transactionType) return false
    if (transactionInvokedBy != other.transactionInvokedBy) return false
    if (txStartTime != other.txStartTime) return false

    return true
  }

  override fun hashCode(): Int {
    var result = transactionType.hashCode()
    result = 31 * result + transactionInvokedBy.hashCode()
    result = 31 * result + txStartTime.hashCode()
    return result
  }
}

enum class TransactionType {
  LOCATION_CREATE,
  LOCATION_UPDATE,
  SYNC,
  MIGRATE,
  CAPACITY_CHANGE,
  CELL_TYPE_CHANGES,
  DEACTIVATION,
  PERMANENT_DEACTIVATION,
  REACTIVATION,
  CELL_CONVERTION_TO_ROOM,
  ROOM_CONVERTION_TO_CELL,
}
