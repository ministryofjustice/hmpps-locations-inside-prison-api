package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.util.SortedSet
import java.util.UUID

@Entity
open class CertificationApprovalRequestLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  private val id: UUID? = null,

  @Column(nullable = false)
  private val locationCode: String,

  @Column(nullable = true)
  private val cellMark: String? = null,

  @Column(nullable = true)
  private val localName: String? = null,

  @Column(nullable = false)
  open val pathHierarchy: String,

  @Column(nullable = false)
  private val level: Int,

  @Column(nullable = true)
  private val certifiedNormalAccommodation: Int? = null,

  @Column(nullable = true)
  private val workingCapacity: Int? = null,

  @Column(nullable = true)
  private val maxCapacity: Int? = null,

  @Column(nullable = true)
  private val inCellSanitation: Boolean? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private val locationType: LocationType,

  @Column(name = "specialist_cell_types", nullable = true)
  private val specialistCellTypes: String? = null,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  private val convertedCellType: ConvertedCellType? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "parent_location_id")
  private val subLocations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

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

    return pathHierarchy == other.pathHierarchy
  }

  private fun getSpecialistCellTypesAsList(): List<SpecialistCellType> = specialistCellTypes?.split(",")?.map { SpecialistCellType.valueOf(it.trim()) } ?: emptyList()

  fun toDto(): CertificationApprovalRequestLocationDto = CertificationApprovalRequestLocationDto(
    id = id!!,
    locationCode = locationCode,
    cellMark = cellMark,
    localName = localName,
    pathHierarchy = pathHierarchy,
    level = level,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
    workingCapacity = workingCapacity,
    maxCapacity = maxCapacity,
    inCellSanitation = inCellSanitation,
    locationType = locationType,
    specialistCellTypes = getSpecialistCellTypesAsList().takeIf { it.isNotEmpty() },
    convertedCellType = convertedCellType,
    subLocations = subLocations.map { it.toDto() }.takeIf { it.isNotEmpty() },
  )
}
