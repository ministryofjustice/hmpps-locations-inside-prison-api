package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToOne
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualLocationCode.entries
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

enum class VirtualLocationCode {
  RECP,
  COURT,
  TAP,
  CSWAP,
}

fun getVirtualLocationCodes() = entries.map { it.name }

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

  @OneToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], optional = true, orphanRemoval = true)
  protected var capacity: Capacity? = null,

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
      countInactiveCells = countInactiveCells,
      includeNonResidential = includeNonResidential,
      useHistoryForUpdate = useHistoryForUpdate,
      countCells = countCells,
      formatLocalName = formatLocalName,
    ).copy(
      oldWorkingCapacity = if (isTemporarilyDeactivated()) {
        getWorkingCapacity()
      } else {
        null
      },
    )

  override fun toLegacyDto(includeHistory: Boolean): LegacyLocation =
    super.toLegacyDto(includeHistory = includeHistory).copy(
      ignoreWorkingCapacity = false,
      capacity = capacity?.toDto(),
    )

  override fun sync(upsert: NomisSyncLocationRequest, clock: Clock): VirtualResidentialLocation {
    super.sync(upsert, clock)
    handleNomisCapacitySync(upsert, upsert.lastUpdatedBy, clock)
    return this
  }
}
