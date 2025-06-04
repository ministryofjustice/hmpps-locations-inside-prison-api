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
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@Entity
open class CellCertificate(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  @Column(nullable = false)
  val prisonId: String,

  @Column(nullable = false)
  val approvedBy: String,

  @Column(nullable = false)
  val approvedDate: LocalDateTime,

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
  @JoinColumn(name = "certification_approval_request_id")
  val certificationApprovalRequest: CertificationApprovalRequest,

  @Column(nullable = false)
  var totalWorkingCapacity: Int = 0,

  @Column(nullable = false)
  var totalMaxCapacity: Int = 0,

  @Column(nullable = false)
  var totalCapacityOfCertifiedCell: Int = 0,

  @Column(nullable = false)
  var current: Boolean = true,

  @SortNatural
  @OneToMany(mappedBy = "cellCertificate", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
  var locations: SortedSet<CellCertificateLocation> = sortedSetOf(),
) {
  override fun toString(): String = "CellCertificate(prisonId='$prisonId', certificationApprovalRequest=$certificationApprovalRequest, current=$current)"
}

@Entity
open class CellCertificateLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cell_certificate_id", nullable = false)
  val cellCertificate: CellCertificate? = null,

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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  var status: DerivedLocationStatus,

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
  val subLocations: SortedSet<CellCertificateLocation> = sortedSetOf(),

) : Comparable<CellCertificateLocation> {

  companion object {
    private val COMPARATOR = compareBy<CellCertificateLocation>
      { it.pathHierarchy }
  }

  override fun compareTo(other: CellCertificateLocation) = COMPARATOR.compare(this, other)

  override fun hashCode(): Int = pathHierarchy.hashCode()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as CellCertificateLocation

    if (pathHierarchy != other.pathHierarchy) return false

    return true
  }

  fun getSpecialistCellTypesAsList(): List<SpecialistCellType> = specialistCellTypes?.split(",")?.map { SpecialistCellType.valueOf(it.trim()) } ?: emptyList()
}
