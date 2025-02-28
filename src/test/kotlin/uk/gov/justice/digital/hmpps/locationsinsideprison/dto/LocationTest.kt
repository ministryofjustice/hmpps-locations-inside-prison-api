package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@JsonInclude(JsonInclude.Include.ALWAYS)
data class LocationTest(
  val id: UUID? = null,
  val prisonId: String? = null,
  val code: String? = null,
  val pathHierarchy: String? = null,
  val locationType: LocationType? = null,
  val localName: String? = null,
  val comments: String? = null,
  val permanentlyInactive: Boolean? = false,
  val permanentlyInactiveReason: String? = null,
  val capacity: Capacity? = null,
  val certification: Certification? = null,
  val usage: List<NonResidentialUsageDto>? = null,
  val accommodationTypes: List<AccommodationType>? = null,
  val specialistCellTypes: List<SpecialistCellType>? = null,
  val usedFor: List<UsedForType>? = null,
  val status: LocationStatus? = null,
  val convertedCellType: ConvertedCellType? = null,
  val otherConvertedCellType: String? = null,
  val active: Boolean? = true,
  val deactivatedByParent: Boolean? = false,
  val deactivatedDate: LocalDateTime? = null,
  val deactivatedReason: DeactivatedReason? = null,
  val deactivatedBy: String? = null,
  val proposedReactivationDate: LocalDate? = null,
  val planetFmReference: String? = null,
  val topLevelId: UUID? = null,
  val level: Int? = null,
  val leafLevel: Boolean? = null,
  val parentId: UUID? = null,
  val parentLocation: Location? = null,
  val inactiveCells: Int? = null,
  val childLocations: List<LocationTest>? = null,
  val changeHistory: List<ChangeHistory>? = null,
  val lastModifiedBy: String? = null,
  val lastModifiedDate: LocalDateTime? = null,
) {

  fun getKey(): String = "$prisonId-$pathHierarchy"

  fun findByPathHierarchy(pathHierarchy: String): LocationTest? {
    val locations = mutableListOf<LocationTest>()

    fun traverse(location: LocationTest) {
      locations.add(location)
      if (location.pathHierarchy == pathHierarchy) {
        return
      }
      location.childLocations?.forEach { childLocation ->
        traverse(childLocation)
      }
    }

    traverse(this)
    return locations.find { it.pathHierarchy == pathHierarchy }
  }

  fun findByKey(key: String): LocationTest? {
    val locations = mutableListOf<LocationTest>()

    fun traverse(location: LocationTest) {
      locations.add(location)
      if (location.getKey() == key) {
        return
      }
      location.childLocations?.forEach { childLocation ->
        traverse(childLocation)
      }
    }

    traverse(this)
    return locations.find { it.getKey() == key }
  }
}
