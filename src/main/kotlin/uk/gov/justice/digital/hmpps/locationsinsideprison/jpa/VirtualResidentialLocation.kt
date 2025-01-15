package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualLocationCode.entries
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

enum class VirtualLocationCode(
  val receptionLocation: Boolean = true,
) {
  RECP,
  COURT,
  TAP,
  CSWAP(false),
}

fun getReceptionLocationCodes() = entries.filter { it.receptionLocation }.map { it.name }

fun getVirtualLocationCodes() = entries.map { it.name }

fun getCSwapLocationCode() = VirtualLocationCode.CSWAP.name

@Entity
@DiscriminatorValue("VIRTUAL")
open class VirtualResidentialLocation(
  id: UUID? = null,
  code: String,
  pathHierarchy: String,
  prisonId: String,
  parent: Location? = null,
  localName: String? = null,
  comments: String? = null,
  orderWithinParentLocation: Int? = 1,
  active: Boolean = true,
  deactivatedDate: LocalDateTime? = null,
  deactivatedReason: DeactivatedReason? = null,
  proposedReactivationDate: LocalDate? = null,
  childLocations: MutableList<Location>,
  whenCreated: LocalDateTime,
  createdBy: String,
  locationType: LocationType = LocationType.AREA,
  residentialHousingType: ResidentialHousingType = ResidentialHousingType.OTHER_USE,
  capacity: Capacity? = null,

) : ResidentialLocation(
  id = id,
  code = code,
  pathHierarchy = pathHierarchy,
  prisonId = prisonId,
  parent = parent,
  localName = localName,
  comments = comments,
  orderWithinParentLocation = orderWithinParentLocation,
  active = active,
  deactivatedDate = deactivatedDate,
  deactivatedReason = deactivatedReason,
  proposedReactivationDate = proposedReactivationDate,
  childLocations = childLocations,
  whenCreated = whenCreated,
  createdBy = createdBy,
  locationType = locationType,
  residentialHousingType = residentialHousingType,
  capacity = capacity,
) {

  override fun toDto(
    includeChildren: Boolean,
    includeParent: Boolean,
    includeHistory: Boolean,
    countInactiveCells: Boolean,
    includeNonResidential: Boolean,
    useHistoryForUpdate: Boolean,
    countCells: Boolean,
    formatLocalName: Boolean,
  ): LocationDto =
    super.toDto(
      includeChildren = includeChildren,
      includeParent = includeParent,
      includeHistory = includeHistory,
      countInactiveCells = false,
      includeNonResidential = includeNonResidential,
      useHistoryForUpdate = useHistoryForUpdate,
      countCells = false,
      formatLocalName = formatLocalName,
    ).copy(
      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = getWorkingCapacity(),
      ),
      certification = null,
      accommodationTypes = null,
      usedFor = null,
      specialistCellTypes = null,
    )

  private fun getWorkingCapacity(): Int {
    return if (isActiveAndAllParentsActive()) {
      capacity?.workingCapacity ?: 0
    } else {
      0
    }
  }

  private fun getMaxCapacity(): Int {
    return if (!isPermanentlyDeactivated()) {
      capacity?.maxCapacity ?: 0
    } else {
      0
    }
  }

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation =
    super.toLegacyDto(includeHistory = includeHistory).copy(
      ignoreWorkingCapacity = false,
      capacity = CapacityDto(
        maxCapacity = getMaxCapacity(),
        workingCapacity = getWorkingCapacity(),
      ),
    )

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): VirtualResidentialLocation {
    super.sync(upsert, clock, linkedTransaction)
    handleNomisCapacitySync(upsert, upsert.lastUpdatedBy, clock, linkedTransaction)
    return this
  }
}
