package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.util.SortedSet
import java.util.UUID

@Entity
open class CertificationApprovalRequestLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "certification_approval_request_id", nullable = false)
  val certificationApprovalRequest: CertificationApprovalRequest,

  @Column(nullable = false)
  val locationCode: String,

  @Column(nullable = true)
  val cellMark: String? = null,

  @Column(nullable = true)
  val localName: String? = null,

  @Column(nullable = false)
  val pathHierarchy: String,

  @Column(nullable = false)
  val level: Int,

  @Column(nullable = true)
  val capacityOfCertifiedCell: Int? = null,

  @Column(nullable = true)
  val workingCapacity: Int? = null,

  @Column(nullable = true)
  val maxCapacity: Int? = null,

  @Column(nullable = true)
  val inCellSanitation: Boolean? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val locationType: LocationType,

  @Column(name = "specialist_cell_types", nullable = true)
  val specialistCellTypes: String? = null,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  val convertedCellType: ConvertedCellType? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "parent_location_id")
  val subLocations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

) : Comparable<CertificationApprovalRequestLocation> {

  companion object {
    private val COMPARATOR = compareBy<CertificationApprovalRequestLocation>
      { it.pathHierarchy }
  }

  override fun compareTo(other: CertificationApprovalRequestLocation) = COMPARATOR.compare(this, other)

  override fun hashCode(): Int = pathHierarchy.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as CertificationApprovalRequestLocation

    if (pathHierarchy != other.pathHierarchy) return false

    return true
  }

  fun getSpecialistCellTypesAsList(): List<SpecialistCellType> = specialistCellTypes?.split(",")?.map { SpecialistCellType.valueOf(it.trim()) } ?: emptyList()
}
