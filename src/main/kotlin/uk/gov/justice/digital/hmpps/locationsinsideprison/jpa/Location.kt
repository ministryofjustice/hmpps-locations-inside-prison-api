package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import org.hibernate.annotations.DiscriminatorFormula
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.SortNatural
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisMigrationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
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
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  private var code: String,

  private var pathHierarchy: String,

  @Enumerated(EnumType.STRING)
  open var locationType: LocationType,

  open val prisonId: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST])
  @JoinColumn(name = "parent_id")
  private var parent: Location? = null,

  open var localName: String? = null,

  open var comments: String? = null,

  open var orderWithinParentLocation: Int? = null,

  private var active: Boolean = true,
  private var archived: Boolean = false,
  private var archivedReason: String? = null,
  open var deactivatedDate: LocalDate? = null,
  @Enumerated(EnumType.STRING)
  open var deactivatedReason: DeactivatedReason? = null,
  open var proposedReactivationDate: LocalDate? = null,
  open var planetFmReference: String? = null,

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  protected open val childLocations: MutableList<Location> = mutableListOf(),

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

  fun addHistory(attributeName: LocationAttribute, oldValue: String?, newValue: String?, amendedBy: String, amendedDate: LocalDateTime): LocationHistory? {
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

  open fun toDto(includeChildren: Boolean = false, includeParent: Boolean = false, includeHistory: Boolean = false, countInactiveCells: Boolean = false): LocationDto {
    return LocationDto(
      id = id!!,
      code = getCode(),
      status = getStatus(),
      locationType = locationType,
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      topLevelId = findTopLevelLocation().id!!,
      lastModifiedDate = whenUpdated,
      lastModifiedBy = updatedBy,
      localName = if (!isCell()) {
        localName
      } else {
        null
      },
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = isActiveAndAllParentsActive(),
      permanentlyInactive = isPermanentlyDeactivated(),
      permanentlyInactiveReason = archivedReason,
      planetFmReference = planetFmReference,
      deactivatedByParent = isActive() && !isActiveAndAllParentsActive(),
      deactivatedDate = findDeactivatedLocationInHierarchy()?.deactivatedDate,
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      proposedReactivationDate = findDeactivatedLocationInHierarchy()?.proposedReactivationDate,
      childLocations = if (includeChildren) childLocations.filter { !it.isPermanentlyDeactivated() }.map { it.toDto(includeChildren = true, includeHistory = includeHistory) } else null,
      parentLocation = if (includeParent) getParent()?.toDto(includeChildren = false, includeParent = true, includeHistory = includeHistory) else null,
      changeHistory = if (includeHistory) history.map { it.toDto() } else null,
      deactivatedBy = deactivatedBy,
    )
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

  open fun updateWith(upsert: UpdateLocationRequest, userOrSystemInContext: String, clock: Clock): Location {
    if (upsert.code != null && this.getCode() != upsert.code) addHistory(LocationAttribute.CODE, getCode(), upsert.code, updatedBy, LocalDateTime.now(clock))
    setCode(upsert.code ?: this.getCode())

    if (upsert.locationType != null && this.locationType != upsert.locationType) {
      addHistory(LocationAttribute.LOCATION_TYPE, this.locationType.description, upsert.locationType?.description, updatedBy, LocalDateTime.now(clock))
    }
    this.locationType = upsert.locationType ?: this.locationType

    if (!isCell()) {
      if (upsert.localName != null && this.localName != upsert.localName) {
        addHistory(LocationAttribute.DESCRIPTION, this.localName, upsert.localName, updatedBy, LocalDateTime.now(clock))
      }
      this.localName = upsert.localName ?: this.localName
    }

    if (upsert.comments != null && this.comments != upsert.comments) {
      addHistory(LocationAttribute.COMMENTS, this.comments, upsert.comments, updatedBy, LocalDateTime.now(clock))
    }
    this.comments = upsert.comments ?: this.comments

    if (upsert.orderWithinParentLocation != null && this.orderWithinParentLocation != upsert.orderWithinParentLocation) {
      addHistory(LocationAttribute.ORDER_WITHIN_PARENT_LOCATION, this.orderWithinParentLocation?.toString(), upsert.orderWithinParentLocation?.toString(), updatedBy, LocalDateTime.now(clock))
    }
    this.orderWithinParentLocation = upsert.orderWithinParentLocation ?: this.orderWithinParentLocation

    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)

    if (upsert is NomisMigrationRequest) {
      updateActiveStatusSyncOnly(upsert, clock, updatedBy)
    }
    return this
  }

  private fun updateActiveStatusSyncOnly(
    upsert: NomisMigrationRequest,
    clock: Clock,
    updatedBy: String,
  ) {
    if (upsert.deactivationReason != this.deactivatedReason) {
      if (upsert.isDeactivated()) {
        temporarilyDeactivate(
          deactivatedReason = upsert.deactivationReason!!.mapsTo(),
          deactivatedDate = upsert.deactivatedDate ?: LocalDate.now(clock),
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
    deactivatedDate: LocalDate,
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
        findSubLocations().forEach { location ->
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
    deactivatedDate: LocalDate,
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
}
