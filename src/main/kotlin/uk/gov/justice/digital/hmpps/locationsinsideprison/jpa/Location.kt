package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.DiscriminatorFormula
import org.hibernate.annotations.SortNatural
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisMigrationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.capitalizeWords
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.UUID
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorFormula("case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' when location_type = 'CELL' then 'CELL' else 'RESIDENTIAL' end")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Location(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  private var code: String,

  private var pathHierarchy: String,

  @Enumerated(EnumType.STRING)
  open var locationType: LocationType,

  open val prisonId: String,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST])
  @JoinColumn(name = "parent_id")
  private var parent: Location? = null,

  open var localName: String? = null,

  open var comments: String? = null,

  open var orderWithinParentLocation: Int? = null,

  private var active: Boolean = true,
  private var archived: Boolean = false,
  private var archivedReason: String? = null,
  open var deactivatedDate: LocalDateTime? = null,
  @Enumerated(EnumType.STRING)
  open var deactivatedReason: DeactivatedReason? = null,
  open var proposedReactivationDate: LocalDate? = null,
  open var planetFmReference: String? = null,

  @BatchSize(size = 1000)
  @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  protected open val childLocations: MutableList<Location> = mutableListOf(),

  @BatchSize(size = 100)
  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  protected open val history: SortedSet<LocationHistory> = sortedSetOf(),

  open val whenCreated: LocalDateTime,
  open var whenUpdated: LocalDateTime,
  open var updatedBy: String,
  open var deactivatedBy: String? = null,
) : Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getKey() = "$prisonId-${getPathHierarchy()}"

  open fun setCode(code: String) {
    this.code = code
    updateHierarchicalPath()
  }

  open fun getDerivedLocationType() = locationType

  open fun getPathHierarchy(): String {
    return pathHierarchy
  }

  open fun setParent(parent: Location) {
    removeParent()
    parent.addChildLocation(this)
  }

  private fun removeParent() {
    parent?.removeChildLocation(this)
    parent = null
  }

  open fun getCode(): String {
    return code
  }

  open fun getParent(): Location? {
    return parent
  }

  private fun findArchivedParent(): Location? {
    fun findArchivedLocation(location: Location?): Location? {
      if (location == null) {
        return null
      }
      if (!location.isArchived()) {
        return findArchivedLocation(location.getParent())
      }
      return location
    }

    return findArchivedLocation(getParent())
  }

  private fun findDeactivatedParent(): Location? {
    fun findDeactivatedLocation(location: Location?): Location? {
      if (location == null) {
        return null
      }
      if (location.isActive()) {
        return findDeactivatedLocation(location.getParent())
      }
      return location
    }

    return findDeactivatedLocation(getParent())
  }

  private fun findDeactivatedLocationInHierarchy(): Location? {
    if (!isActive()) {
      return this
    }
    return findDeactivatedParent()
  }

  private fun findArchivedLocationInHierarchy(): Location? {
    if (isArchived()) {
      return this
    }
    return findArchivedParent()
  }

  open fun isActive() = active

  open fun isArchived() = archived

  open fun isActiveAndAllParentsActive() = isActive() && !hasDeactivatedParent()

  open fun isTemporarilyDeactivated() = !isActiveAndAllParentsActive() && !isPermanentlyDeactivated()

  private fun hasDeactivatedParent() = findDeactivatedParent() != null

  open fun isPermanentlyDeactivated() = findArchivedLocationInHierarchy()?.archived ?: false

  fun addChildLocation(childLocation: Location): Location {
    childLocation.parent = this
    childLocations.add(childLocation)
    childLocation.updateHierarchicalPath()
    return this
  }

  private fun removeChildLocation(childLocation: Location): Location {
    childLocation.parent = null
    childLocations.remove(childLocation)
    childLocation.updateHierarchicalPath() // recalculate path hierarchy
    return this
  }

  fun findTopLevelLocation(): Location {
    return getParent()?.findTopLevelLocation() ?: this
  }

  private fun getLevel(): Int {
    fun goUp(location: Location?, level: Int): Int {
      if (location == null) {
        return level
      }
      return goUp(location.getParent(), level.inc())
    }

    return goUp(this, 0)
  }

  fun getHierarchy(): List<LocationSummary> {
    val locationSummary = mutableListOf<LocationSummary>()

    fun goUp(location: Location?) {
      if (location != null) {
        locationSummary.add(location.getLocationSummary())
        goUp(location.parent)
      }
    }

    goUp(this)
    return locationSummary.sortedBy { it.level }
  }

  private fun getLocationSummary(): LocationSummary {
    return LocationSummary(
      id = id,
      code = getCode(),
      type = getDerivedLocationType(),
      pathHierarchy = getPathHierarchy(),
      prisonId = prisonId,
      localName = getDerivedLocalName(),
      level = getLevel(),
    )
  }

  private fun updateHierarchicalPath() {
    pathHierarchy = getHierarchicalPath()
    for (childLocation in childLocations) {
      childLocation.updateHierarchicalPath()
    }
  }

  private fun getHierarchicalPath(): String {
    return if (getParent() == null) {
      getCode()
    } else {
      "${getParent()!!.getHierarchicalPath()}-${getCode()}"
    }
  }

  fun getActiveResidentialLocationsBelowThisLevel() = childLocations.filterIsInstance<ResidentialLocation>().filter { it.isActiveAndAllParentsActive() }

  fun cellLocations() = findAllLeafLocations().filterIsInstance<Cell>()

  fun findAllLeafLocations(): List<Location> {
    val leafLocations = mutableListOf<Location>()

    fun traverse(location: Location) {
      if (location.childLocations.isEmpty()) {
        leafLocations.add(location)
      } else {
        for (childLocation in location.childLocations) {
          traverse(childLocation)
        }
      }
    }

    traverse(this)
    return leafLocations
  }

  fun findSubLocations(): List<Location> {
    val subLocations = mutableListOf<Location>()

    fun traverse(location: Location) {
      if (this != location) {
        subLocations.add(location)
      }
      for (childLocation in location.childLocations) {
        traverse(childLocation)
      }
    }

    traverse(this)
    return subLocations
  }

  fun addHistory(
    attributeName: LocationAttribute,
    oldValue: String?,
    newValue: String?,
    amendedBy: String,
    amendedDate: LocalDateTime,
  ): LocationHistory? {
    return if (oldValue != newValue) {
      val locationHistory = LocationHistory(
        location = this,
        attributeName = attributeName,
        oldValue = oldValue,
        newValue = newValue,
        amendedBy = amendedBy,
        amendedDate = amendedDate,
      )
      history.add(locationHistory)
      return locationHistory
    } else {
      null
    }
  }

  open fun getHistoryAsList() = history.toList()

  open fun toDto(
    includeChildren: Boolean = false,
    includeParent: Boolean = false,
    includeHistory: Boolean = false,
    countInactiveCells: Boolean = false,
  ): LocationDto {
    return LocationDto(
      id = id!!,
      code = getCode(),
      status = getStatus(),
      locationType = getDerivedLocationType(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      topLevelId = findTopLevelLocation().id!!,
      level = getLevel(),
      leafLevel = findSubLocations().isEmpty(),
      lastModifiedDate = whenUpdated,
      lastModifiedBy = updatedBy,
      localName = getDerivedLocalName(),
      comments = comments,
      active = isActiveAndAllParentsActive(),
      permanentlyInactive = isPermanentlyDeactivated(),
      permanentlyInactiveReason = archivedReason,
      planetFmReference = planetFmReference,
      deactivatedByParent = isActive() && !isActiveAndAllParentsActive(),
      deactivatedDate = findDeactivatedLocationInHierarchy()?.deactivatedDate,
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      proposedReactivationDate = findDeactivatedLocationInHierarchy()?.proposedReactivationDate,
      childLocations = if (includeChildren) {
        childLocations.filter { !it.isPermanentlyDeactivated() }
          .map { it.toDto(includeChildren = true, includeHistory = includeHistory) }
      } else {
        null
      },
      parentLocation = if (includeParent) {
        getParent()?.toDto(
          includeChildren = false,
          includeParent = true,
          includeHistory = includeHistory,
        )
      } else {
        null
      },
      changeHistory = if (includeHistory) history.map { it.toDto() } else null,
      deactivatedBy = deactivatedBy,
    )
  }

  fun toLocationGroupDto(): LocationGroupDto {
    return LocationGroupDto(
      key = pathHierarchy,
      name = getDerivedLocalName(),
      children = getActiveResidentialLocationsBelowThisLevel().map { it.toLocationGroupDto() },
    )
  }

  fun getDerivedLocalName() = if (!isCell()) {
    localName?.capitalizeWords()
  } else {
    null
  }

  fun getStatus(): LocationStatus {
    return if (isActiveAndAllParentsActive()) {
      if (isNonResCell()) {
        LocationStatus.NON_RESIDENTIAL
      } else {
        LocationStatus.ACTIVE
      }
    } else {
      LocationStatus.INACTIVE
    }
  }

  private fun isNonResCell() = this is Cell && convertedCellType != null

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    return getKey() == other.getKey()
  }

  override fun hashCode(): Int {
    return getKey().hashCode()
  }

  open fun updateLocalName(localName: String?, userOrSystemInContext: String, clock: Clock) {
    if (!isCell()) {
      addHistory(LocationAttribute.DESCRIPTION, this.localName, localName, updatedBy, LocalDateTime.now(clock))
      this.localName = localName
    }
  }

  open fun updateComments(comments: String?, userOrSystemInContext: String, clock: Clock) {
    addHistory(LocationAttribute.COMMENTS, this.comments, comments, updatedBy, LocalDateTime.now(clock))
    this.comments = comments
  }

  open fun updateCode(code: String?, userOrSystemInContext: String, clock: Clock): Location {
    if (code != null && this.getCode() != code) {
      addHistory(LocationAttribute.CODE, getCode(), code, updatedBy, LocalDateTime.now(clock))
      setCode(code)
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = LocalDateTime.now(clock)
    }
    return this
  }

  open fun sync(upsert: NomisSyncLocationRequest, userOrSystemInContext: String, clock: Clock): Location {
    addHistory(LocationAttribute.CODE, getCode(), upsert.code, updatedBy, LocalDateTime.now(clock))
    setCode(upsert.code)

    addHistory(LocationAttribute.LOCATION_TYPE, getDerivedLocationType().description, upsert.locationType.description, updatedBy, LocalDateTime.now(clock))
    this.locationType = upsert.locationType

    addHistory(LocationAttribute.DESCRIPTION, this.localName, upsert.localName, updatedBy, LocalDateTime.now(clock))
    this.localName = upsert.localName

    addHistory(LocationAttribute.COMMENTS, this.comments, upsert.comments, updatedBy, LocalDateTime.now(clock))
    this.comments = upsert.comments

    addHistory(LocationAttribute.ORDER_WITHIN_PARENT_LOCATION, this.orderWithinParentLocation?.toString(), upsert.orderWithinParentLocation?.toString(), updatedBy, LocalDateTime.now(clock))
    this.orderWithinParentLocation = upsert.orderWithinParentLocation

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)

    updateActiveStatusSyncOnly(upsert, clock, updatedBy)
    return this
  }

  private fun updateActiveStatusSyncOnly(
    upsert: NomisMigrationRequest,
    clock: Clock,
    updatedBy: String,
  ) {
    if (upsert.deactivationReason?.mapsTo() != this.deactivatedReason) {
      if (upsert.isDeactivated()) {
        temporarilyDeactivate(
          deactivatedReason = upsert.deactivationReason!!.mapsTo(),
          deactivatedDate = upsert.deactivatedDate?.atStartOfDay() ?: LocalDateTime.now(clock),
          proposedReactivationDate = upsert.proposedReactivationDate,
          userOrSystemInContext = updatedBy,
          clock = clock,
        )
      } else {
        reactivate(updatedBy, clock)
      }
    }
  }

  open fun temporarilyDeactivate(
    deactivatedReason: DeactivatedReason,
    deactivatedDate: LocalDateTime,
    planetFmReference: String? = null,
    proposedReactivationDate: LocalDate? = null,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    if (!isActive()) {
      log.warn("Location [${getKey()}] is already deactivated")
    } else {
      val amendedDate = LocalDateTime.now(clock)
      addHistory(LocationAttribute.ACTIVE, "true", "false", userOrSystemInContext, amendedDate)
      addHistory(
        LocationAttribute.DEACTIVATED_REASON,
        this.deactivatedReason?.description,
        deactivatedReason.description,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.DEACTIVATED_DATE,
        this.deactivatedDate?.toString(),
        deactivatedDate.toString(),
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        this.proposedReactivationDate?.toString(),
        proposedReactivationDate?.toString(),
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PLANET_FM_NUMBER,
        this.planetFmReference,
        planetFmReference,
        userOrSystemInContext,
        amendedDate,
      )

      this.active = false
      this.deactivatedReason = deactivatedReason
      this.deactivatedDate = deactivatedDate
      this.proposedReactivationDate = proposedReactivationDate
      this.planetFmReference = planetFmReference
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate
      this.deactivatedBy = userOrSystemInContext

      if (this is ResidentialLocation) {
        findSubLocations().filterIsInstance<ResidentialLocation>().forEach { location ->
          location.temporarilyDeactivate(
            planetFmReference = planetFmReference,
            proposedReactivationDate = proposedReactivationDate,
            deactivatedReason = deactivatedReason,
            deactivatedDate = deactivatedDate,
            userOrSystemInContext = userOrSystemInContext,
            clock = clock,
          )
        }
      }

      log.info("Temporarily Deactivated Location [${getKey()}]")
    }
  }

  open fun updateDeactivatedDetails(
    deactivatedReason: DeactivatedReason,
    planetFmReference: String? = null,
    proposedReactivationDate: LocalDate? = null,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    if (!isTemporarilyDeactivated()) {
      log.warn("Location [${getKey()}] is not deactivated")
    } else {
      val amendedDate = LocalDateTime.now(clock)
      addHistory(
        LocationAttribute.DEACTIVATED_REASON,
        this.deactivatedReason?.description,
        deactivatedReason.description,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        this.proposedReactivationDate?.toString(),
        proposedReactivationDate?.toString(),
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PLANET_FM_NUMBER,
        this.planetFmReference,
        planetFmReference,
        userOrSystemInContext,
        amendedDate,
      )

      this.deactivatedReason = deactivatedReason
      this.proposedReactivationDate = proposedReactivationDate
      this.planetFmReference = planetFmReference
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate

      if (this is ResidentialLocation) {
        findSubLocations().forEach { location ->
          location.updateDeactivatedDetails(
            planetFmReference = planetFmReference,
            proposedReactivationDate = proposedReactivationDate,
            deactivatedReason = deactivatedReason,
            userOrSystemInContext = userOrSystemInContext,
            clock = clock,
          )
        }
      }

      log.info("Temporarily Deactivated Location Updated [${getKey()}]")
    }
  }

  open fun permanentlyDeactivate(
    reason: String,
    deactivatedDate: LocalDateTime,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    if (isPermanentlyDeactivated()) {
      log.warn("Location [${getKey()}] is already permanently deactivated")
    } else {
      val amendedDate = LocalDateTime.now(clock)
      addHistory(LocationAttribute.ACTIVE, "true", "false", userOrSystemInContext, amendedDate)
      addHistory(
        LocationAttribute.PERMANENT_DEACTIVATION,
        null,
        reason,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.DEACTIVATED_DATE,
        this.deactivatedDate?.toString(),
        deactivatedDate.toString(),
        userOrSystemInContext,
        amendedDate,
      )

      this.archived = true
      this.active = false
      this.deactivatedDate = deactivatedDate
      this.deactivatedReason = null
      this.proposedReactivationDate = null
      this.planetFmReference = null
      this.deactivatedBy = userOrSystemInContext
      this.archivedReason = reason
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate

      if (this is ResidentialLocation) {
        this.cellLocations().forEach { cellLocation ->
          cellLocation.setCapacity(maxCapacity = 0, workingCapacity = 0, userOrSystemInContext, clock)
          cellLocation.deCertifyCell(userOrSystemInContext, clock)
        }
      }
      log.info("Permanently Deactivated Location [${getKey()}]")
    }
  }

  open fun reactivate(userOrSystemInContext: String, clock: Clock) {
    this.getParent()?.reactivate(userOrSystemInContext, clock)
    if (!isActive()) {
      if (isPermanentlyDeactivated()) {
        throw LocationCannotBeReactivatedException("Location [${getKey()}] permanently deactivated")
      }

      val amendedDate = LocalDateTime.now(clock)
      addHistory(LocationAttribute.ACTIVE, "false", "true", userOrSystemInContext, amendedDate)
      addHistory(
        LocationAttribute.DEACTIVATED_REASON,
        deactivatedReason?.description,
        null,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.DEACTIVATED_DATE,
        deactivatedDate?.toString(),
        null,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        proposedReactivationDate?.toString(),
        null,
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PLANET_FM_NUMBER,
        planetFmReference,
        null,
        userOrSystemInContext,
        amendedDate,
      )

      this.active = true
      this.deactivatedReason = null
      this.deactivatedDate = null
      this.planetFmReference = null
      this.proposedReactivationDate = null
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate
      this.deactivatedBy = null

      log.info("Re-activated Location [${getKey()}]")
    }
  }

  override fun toString(): String {
    return getKey()
  }

  fun isCell() = locationType == LocationType.CELL
  fun isWingLandingSpur() = locationType in listOf(LocationType.WING, LocationType.LANDING, LocationType.SPUR)

  fun isResidentialType() = locationType in ResidentialLocationType.entries.map { it.baseType }

  open fun toLegacyDto(includeHistory: Boolean = false): LegacyLocation {
    return LegacyLocation(
      id = id!!,
      code = getCode(),
      locationType = locationType,
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      lastModifiedDate = whenUpdated,
      lastModifiedBy = updatedBy,
      localName = localName,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = isActiveAndAllParentsActive(),
      deactivatedDate = findDeactivatedLocationInHierarchy()?.deactivatedDate?.toLocalDate(),
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      proposedReactivationDate = findDeactivatedLocationInHierarchy()?.proposedReactivationDate,
      changeHistory = if (includeHistory) history.map { it.toDto() } else null,
    )
  }
}

@Schema(description = "Location Hierarchy Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationSummary(
  @Schema(description = "ID of location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val id: UUID? = null,
  @Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  val prisonId: String,
  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  val code: String,
  @Schema(description = "Location type", example = "WING", required = true)
  val type: LocationType,
  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  val localName: String? = null,
  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String? = null,
  @Schema(description = "Current Level within hierarchy, starts at 1, e.g Wing = 1", examples = ["1", "2", "3"], required = true)
  val level: Int,
)
