package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.hibernate.annotations.SortNatural
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
  val prisonId: String,
  var transactionDetail: String,
  val transactionInvokedBy: String,
  val txStartTime: LocalDateTime,
  var txEndTime: LocalDateTime? = null,

  @OneToMany(mappedBy = "linkedTransaction", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  val auditChanges: SortedSet<LocationHistory> = sortedSetOf(),
) : Comparable<LinkedTransaction> {

  companion object {
    private val COMPARATOR = compareBy<LinkedTransaction>
      { it.transactionId }
      .thenBy { it.transactionType }
      .thenBy { it.transactionInvokedBy }
      .thenBy { it.txStartTime }
  }

  override fun compareTo(other: LinkedTransaction) = COMPARATOR.compare(this, other)

  fun toDto(filterLocation: Location? = null) = TransactionHistory(
    transactionId = transactionId!!,
    prisonId = prisonId,
    transactionType = transactionType,
    transactionDetail = transactionDetail,
    transactionInvokedBy = transactionInvokedBy,
    txStartTime = txStartTime,
    txEndTime = txEndTime,
    transactionDetails = auditChanges
      .asSequence()
      .filter { audit -> (filterLocation == null || audit.location == filterLocation) && audit.attributeName.display }
      .sortedBy { it.amendedDate }.sortedBy { it.attributeName.displayOrder }
      .groupBy {
        Pair(it.location, it.attributeName)
      }
      .mapNotNull { (locationAttributePair, groupedTx) -> toGroupedTx(locationAttributePair.second, groupedTx) }
      .toList(),
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LinkedTransaction

    if (transactionId != other.transactionId) return false
    if (transactionType != other.transactionType) return false
    if (transactionInvokedBy != other.transactionInvokedBy) return false
    if (txStartTime != other.txStartTime) return false

    return true
  }

  override fun hashCode(): Int {
    var result = transactionId.hashCode()
    result = 31 * result + transactionType.hashCode()
    result = 31 * result + transactionInvokedBy.hashCode()
    result = 31 * result + txStartTime.hashCode()
    return result
  }
  override fun toString(): String = "$transactionId: $transactionType at $txStartTime by $transactionInvokedBy, ($transactionDetail) )"
}

enum class TransactionType {
  LOCATION_CREATE,
  LOCATION_CREATE_NON_RESI,
  LOCATION_UPDATE,
  LOCATION_UPDATE_NON_RESI,
  SYNC,
  SYNC_NON_RESIDENTIAL,
  DELETE,
  CAPACITY_CHANGE,
  CELL_TYPE_CHANGES,
  DEACTIVATION,
  PERMANENT_DEACTIVATION,
  REACTIVATION,
  CELL_CONVERTION_TO_ROOM,
  ROOM_CONVERTION_TO_CELL,
  SIGNED_OP_CAP,
  RESI_SERVICE_ACTIVATION,
  NON_RESI_SERVICE_ACTIVATION,
  INCLUDE_SEG_IN_ROLL_COUNT_ACTIVATION,
  APPROVAL_PROCESS_ACTIVATION,
  PENDING_CELL_CHANGE,
  REQUEST_CERTIFICATION_APPROVAL,
  APPROVE_CERTIFICATION_REQUEST,
  REJECT_CERTIFICATION_REQUEST,
  WITHDRAW_CERTIFICATION_REQUEST,
}
