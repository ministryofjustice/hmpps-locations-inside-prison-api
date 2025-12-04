package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.NamedEntityGraphs
import jakarta.persistence.NamedSubgraph
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CellCertificateLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID

@NamedEntityGraphs(
  value = [
    NamedEntityGraph(
      name = "CellCertificate.eager",
      attributeNodes = [
        NamedAttributeNode("certificationApprovalRequest"),
        NamedAttributeNode("locations", subgraph = "certificate.eager.subgraph"),
      ],
      subgraphs = [
        NamedSubgraph(
          name = "certificate.eager.subgraph",
          attributeNodes = [
            NamedAttributeNode("subLocations"),
          ],
        ),
      ],
    ),
  ],
)
@Entity
open class CellCertificate(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  private val prisonId: String,

  @Column(nullable = false)
  private val approvedBy: String,

  @Column(nullable = false)
  private val approvedDate: LocalDateTime,

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE], optional = false)
  @JoinColumn(name = "certification_approval_request_id", nullable = false)
  private val certificationApprovalRequest: CertificationApprovalRequest,

  @Column(nullable = false)
  open var totalWorkingCapacity: Int = 0,

  @Column(nullable = false)
  open var totalMaxCapacity: Int = 0,

  @Column(nullable = false)
  open var totalCertifiedNormalAccommodation: Int = 0,

  @Column(nullable = false)
  open var signedOperationCapacity: Int = 0,

  @Column(nullable = false)
  private var current: Boolean = true,

  @SortNatural
  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "cell_certificate_id", nullable = false)
  open var locations: SortedSet<CellCertificateLocation> = sortedSetOf(),
) {
  override fun toString(): String = "CellCertificate(prisonId='$prisonId', certificationApprovalRequest=$certificationApprovalRequest, current=$current)"

  fun markAsNotCurrent() {
    current = false
  }

  fun toDto(showLocations: Boolean = false): CellCertificateDto = CellCertificateDto(
    id = id!!,
    prisonId = prisonId,
    approvedBy = approvedBy,
    approvedDate = approvedDate,
    certificationApprovalRequestId = certificationApprovalRequest.id!!,
    totalWorkingCapacity = totalWorkingCapacity,
    totalMaxCapacity = totalMaxCapacity,
    totalCertifiedNormalAccommodation = totalCertifiedNormalAccommodation,
    signedOperationCapacity = signedOperationCapacity,
    current = current,
    approvedRequest = certificationApprovalRequest.toDto(),
    locations = if (showLocations) {
      locations.filter { it.level == 1 } // Only include top-level locations
        .map { it.toDto() }
    } else {
      null
    },
  )
}

@Entity
open class CellCertificateLocation(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  @Column(nullable = false)
  private val locationCode: String,

  @Column(nullable = true)
  private val cellMark: String? = null,

  @Column(nullable = true)
  private val localName: String? = null,

  @Column(nullable = false)
  private val pathHierarchy: String,

  @Column(nullable = false)
  open val level: Int,

  @Column(nullable = true)
  open val certifiedNormalAccommodation: Int? = null,

  @Column(nullable = true)
  open val workingCapacity: Int? = null,

  @Column(nullable = true)
  open val maxCapacity: Int? = null,

  @Column(nullable = true)
  private val inCellSanitation: Boolean? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private val locationType: LocationType,

  private val specialistCellTypes: String? = null,

  private val usedForTypes: String? = null,

  private val accommodationTypes: String? = null,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  private val convertedCellType: ConvertedCellType? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "parent_location_id")
  private val subLocations: SortedSet<CellCertificateLocation> = sortedSetOf(),

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

    return pathHierarchy == other.pathHierarchy
  }

  fun toDto(): CellCertificateLocationDto = CellCertificateLocationDto(
    locationCode = locationCode,
    pathHierarchy = pathHierarchy,
    certifiedNormalAccommodation = certifiedNormalAccommodation,
    workingCapacity = workingCapacity,
    maxCapacity = maxCapacity,
    inCellSanitation = inCellSanitation,
    locationType = locationType,
    specialistCellTypes = getSpecialistCellTypesFromList(),
    accommodationTypes = getAccommodationTypesFromList(),
    usedFor = getUsedForTypesFromList(),
    localName = localName,
    cellMark = cellMark,
    level = level,
    convertedCellType = convertedCellType,
    subLocations = subLocations.map { it.toDto() },
  )

  private fun getSpecialistCellTypesFromList(): List<SpecialistCellType>? = specialistCellTypes?.split(",")?.map { SpecialistCellType.valueOf(it.trim()) }

  private fun getUsedForTypesFromList(): List<UsedForType>? = usedForTypes?.split(",")?.map { UsedForType.valueOf(it.trim()) }

  private fun getAccommodationTypesFromList(): List<AccommodationType>? = accommodationTypes?.split(",")?.map { AccommodationType.valueOf(it.trim()) }
}
