package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest

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
import org.hibernate.annotations.SortNatural
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CertificationApprovalRequestLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
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
  open val level: Int,

  @Column(nullable = true)
  open var currentCertifiedNormalAccommodation: Int? = null,

  @Column(nullable = true)
  open var certifiedNormalAccommodation: Int? = null,

  @Column(nullable = true)
  open var currentWorkingCapacity: Int? = null,

  @Column(nullable = true)
  open var workingCapacity: Int? = null,

  @Column(nullable = true)
  open var currentMaxCapacity: Int? = null,

  @Column(nullable = true)
  open var maxCapacity: Int? = null,

  @Column(nullable = true)
  private val inCellSanitation: Boolean? = null,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val locationType: LocationType,

  open var currentSpecialistCellTypes: String? = null,

  open var specialistCellTypes: String? = null,

  private val usedForTypes: String? = null,

  private val accommodationTypes: String? = null,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  private val convertedCellType: ConvertedCellType? = null,

  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @JoinColumn(name = "parent_location_id")
  @SortNatural
  private val subLocations: SortedSet<CertificationApprovalRequestLocation> = sortedSetOf(),

  @Column(nullable = false)
  open var reactivateThisLocation: Boolean = false,

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
    currentCertifiedNormalAccommodation = currentCertifiedNormalAccommodation,
    currentWorkingCapacity = currentWorkingCapacity,
    currentMaxCapacity = currentMaxCapacity,
    inCellSanitation = inCellSanitation,
    locationType = locationType,
    currentSpecialistCellTypes = getSpecialistCellTypesFromList(currentSpecialistCellTypes),
    specialistCellTypes = getSpecialistCellTypesFromList(specialistCellTypes),
    accommodationTypes = getAccommodationTypesFromList(),
    usedFor = getUsedForTypesFromList(),
    convertedCellType = convertedCellType,
    reactivateThisLocation = reactivateThisLocation,
    subLocations = subLocations.map { it.toDto() }.takeIf { it.isNotEmpty() },
  )

  fun getSpecialistCellTypesFromList(): List<SpecialistCellType>? = specialistCellTypes?.let { types ->
    types.takeIf { it.isNotEmpty() }?.let { getSpecialistCellTypesFromList(it) } ?: emptyList()
  }

  private fun getUsedForTypesFromList(): List<UsedForType>? = usedForTypes?.split(",")?.map { UsedForType.valueOf(it.trim()) }

  private fun getAccommodationTypesFromList(): List<AccommodationType>? = accommodationTypes?.split(",")?.map { AccommodationType.valueOf(it.trim()) }

  fun findAllLeafLocations(): List<CertificationApprovalRequestLocation> {
    val leafLocations = mutableListOf<CertificationApprovalRequestLocation>()

    fun traverse(location: CertificationApprovalRequestLocation) {
      if (location.subLocations.isEmpty()) {
        leafLocations.add(location)
      } else {
        for (childLocation in location.subLocations) {
          traverse(childLocation)
        }
      }
    }

    traverse(this)
    return leafLocations
  }

  fun findLocationByPathHierarchy(pathHierarchy: String): CertificationApprovalRequestLocation? = findSubLocations().find { it.pathHierarchy == pathHierarchy }

  fun findSubLocations(): List<CertificationApprovalRequestLocation> {
    val subLocations = mutableListOf<CertificationApprovalRequestLocation>()

    fun traverse(location: CertificationApprovalRequestLocation) {
      if (this != location) {
        subLocations.add(location)
      }
      for (childLocation in location.subLocations) {
        traverse(childLocation)
      }
    }

    traverse(this)
    return subLocations
  }

  fun approvedWorkingCapacity(): Int = findAllLeafLocations().sumOf { it.workingCapacity ?: 0 }

  fun approvedMaxCapacity(): Int = findAllLeafLocations().sumOf { it.maxCapacity ?: 0 }

  fun approvedCertifiedNormalAccommodation(): Int = findAllLeafLocations().sumOf { it.certifiedNormalAccommodation ?: 0 }

  fun calcCurrentWorkingCapacity(): Int = findAllLeafLocations().sumOf { it.currentWorkingCapacity ?: 0 }

  fun calcCurrentMaxCapacity(): Int = findAllLeafLocations().sumOf { it.currentMaxCapacity ?: 0 }

  fun calcCurrentCertifiedNormalAccommodation(): Int = findAllLeafLocations().sumOf { it.currentCertifiedNormalAccommodation ?: 0 }

  fun workingCapacityChange(): Int = approvedWorkingCapacity() - calcCurrentWorkingCapacity()

  fun maxCapacityChange(): Int = approvedMaxCapacity() - calcCurrentMaxCapacity()

  fun certifiedNormalAccommodationChange(): Int = approvedCertifiedNormalAccommodation() - calcCurrentCertifiedNormalAccommodation()

  fun getSpecialistCellTypesFromList(specialCellTypes: String?): List<SpecialistCellType>? = specialCellTypes?.let { types ->
    types.takeIf { it.isNotEmpty() }
      ?.split(",")
      ?.map { SpecialistCellType.valueOf(it.trim()) }
      ?: emptyList()
  }

  fun refreshCapacities() {
    workingCapacity = approvedWorkingCapacity()
    maxCapacity = approvedMaxCapacity()
    certifiedNormalAccommodation = approvedCertifiedNormalAccommodation()
    currentWorkingCapacity = calcCurrentWorkingCapacity()
    currentMaxCapacity = calcCurrentMaxCapacity()
    currentCertifiedNormalAccommodation = calcCurrentCertifiedNormalAccommodation()
  }
}
