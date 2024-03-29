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
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationCannotBeReactivatedException
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
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

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  @JoinColumn(name = "parent_id")
  private var parent: Location? = null,

  open var localName: String? = null,

  open var comments: String? = null,

  open var orderWithinParentLocation: Int? = null,

  private var active: Boolean = true,
  open var deactivatedDate: LocalDate? = null,
  @Enumerated(EnumType.STRING)
  open var deactivatedReason: DeactivatedReason? = null,
  open var proposedReactivationDate: LocalDate? = null,

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  protected open val childLocations: MutableList<Location> = mutableListOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  protected open val history: SortedSet<LocationHistory> = sortedSetOf(),

  open val whenCreated: LocalDateTime,
  open var whenUpdated: LocalDateTime,
  open var updatedBy: String,
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

  open fun isActive(): Boolean {
    return active
  }

  open fun isActiveAndAllParentsActive(): Boolean {
    return isActive() && !hasDeactivatedParent()
  }

  private fun hasDeactivatedParent() = findDeactivatedParent() != null

  open fun isPermanentlyInactive(): Boolean {
    return findDeactivatedLocationInHierarchy()?.deactivatedReason in permanentlyInactiveReasons()
  }

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

  open fun toDto(includeChildren: Boolean = false, includeParent: Boolean = false, includeHistory: Boolean = false): LocationDto {
    return LocationDto(
      id = id!!,
      code = getCode(),
      locationType = locationType,
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      topLevelId = findTopLevelLocation().id!!,
      localName = localName,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = isActiveAndAllParentsActive(),
      permanentlyInactive = isPermanentlyInactive(),
      deactivatedByParent = isActive() && !isActiveAndAllParentsActive(),
      deactivatedDate = findDeactivatedLocationInHierarchy()?.deactivatedDate,
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      proposedReactivationDate = findDeactivatedLocationInHierarchy()?.proposedReactivationDate,
      childLocations = if (includeChildren) childLocations.map { it.toDto(includeChildren = true, includeHistory = includeHistory) } else null,
      parentLocation = if (includeParent) getParent()?.toDto(includeChildren = false, includeParent = true, includeHistory = includeHistory) else null,
      changeHistory = if (includeHistory) history.map { it.toDto() } else null,
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    return getKey() == other.getKey()
  }

  override fun hashCode(): Int {
    return getKey().hashCode()
  }

  open fun updateWith(upsert: UpdateLocationRequest, updatedBy: String, clock: Clock): Location {
    if (upsert.code != null && this.getCode() != upsert.code) addHistory(LocationAttribute.CODE, getCode(), upsert.code, updatedBy, LocalDateTime.now(clock))
    setCode(upsert.code ?: this.getCode())

    if (upsert.locationType != null && this.locationType != upsert.locationType) {
      addHistory(LocationAttribute.LOCATION_TYPE, this.locationType.description, upsert.locationType?.description, updatedBy, LocalDateTime.now(clock))
    }
    this.locationType = upsert.locationType ?: this.locationType

    if (upsert.localName != null && this.localName != upsert.localName) {
      addHistory(LocationAttribute.DESCRIPTION, this.localName, upsert.localName, updatedBy, LocalDateTime.now(clock))
    }
    this.localName = upsert.localName ?: this.localName

    if (upsert.comments != null && this.comments != upsert.comments) {
      addHistory(LocationAttribute.COMMENTS, this.comments, upsert.comments, updatedBy, LocalDateTime.now(clock))
    }
    this.comments = upsert.comments ?: this.comments

    if (upsert.orderWithinParentLocation != null && this.orderWithinParentLocation != upsert.orderWithinParentLocation) {
      addHistory(LocationAttribute.ORDER_WITHIN_PARENT_LOCATION, this.orderWithinParentLocation?.toString(), upsert.orderWithinParentLocation?.toString(), updatedBy, LocalDateTime.now(clock))
    }
    this.orderWithinParentLocation = upsert.orderWithinParentLocation ?: this.orderWithinParentLocation

    if (this.deactivatedReason != upsert.deactivationReason) {
      if (upsert.isDeactivated()) {
        deactivate(
          deactivatedReason = upsert.deactivationReason!!,
          deactivatedDate = upsert.deactivatedDate?.atStartOfDay() ?: LocalDateTime.now(clock),
          proposedReactivationDate = upsert.proposedReactivationDate,
          userOrSystemInContext = updatedBy,
          clock = clock,
        )
      } else {
        reactivate(updatedBy, clock)
      }
    }
    this.updatedBy = updatedBy
    this.whenUpdated = LocalDateTime.now(clock)

    return this
  }

  fun deactivate(
    deactivatedReason: DeactivatedReason,
    deactivatedDate: LocalDateTime?,
    proposedReactivationDate: LocalDate? = null,
    userOrSystemInContext: String,
    clock: Clock,
  ) {
    if (!isActive()) {
      log.warn("Location [$id] is already deactivated")
    } else {
      val amendedDate = deactivatedDate ?: LocalDateTime.now(clock)
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
        this.deactivatedDate.toString(),
        deactivatedDate.toString(),
        userOrSystemInContext,
        amendedDate,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        this.proposedReactivationDate.toString(),
        proposedReactivationDate?.toString(),
        userOrSystemInContext,
        amendedDate,
      )

      this.active = false
      this.deactivatedReason = deactivatedReason
      this.deactivatedDate = deactivatedDate?.toLocalDate()
      this.proposedReactivationDate = proposedReactivationDate
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate

      log.info("Deactivated Location [$id]")
    }
  }

  fun reactivate(userOrSystemInContext: String, clock: Clock) {
    if (isActive()) {
      throw LocationCannotBeReactivatedException("Location [$id] is already active")
    }
    addHistory(LocationAttribute.ACTIVE, "false", "true", userOrSystemInContext, LocalDateTime.now(clock))
    addHistory(LocationAttribute.DEACTIVATED_REASON, deactivatedReason?.description, null, userOrSystemInContext, LocalDateTime.now(clock))
    addHistory(LocationAttribute.DEACTIVATED_DATE, deactivatedDate?.toString(), null, userOrSystemInContext, LocalDateTime.now(clock))
    addHistory(LocationAttribute.PROPOSED_REACTIVATION_DATE, proposedReactivationDate?.toString(), null, userOrSystemInContext, LocalDateTime.now(clock))

    this.active = true
    this.deactivatedReason = null
    this.deactivatedDate = null
    this.proposedReactivationDate = null
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)

    log.info("Re-activated Location [$id]")
  }

  override fun toString(): String {
    return getKey()
  }

  fun isCell() = locationType == LocationType.CELL
  fun isWingLandingSpur() = locationType in listOf(LocationType.WING, LocationType.LANDING, LocationType.SPUR)
}

enum class LocationType(
  val description: String,
) {
  WING("Wing"),
  SPUR("Spur"),
  LANDING("Landing"),
  CELL("Cell"),
  ROOM("Room"),
  HOLDING_AREA("Holding Area"),
  MOVEMENT_AREA("Movement Area"),
  RESIDENTIAL_UNIT("Residential Unit"),
  EXTERNAL_GROUNDS("External Grounds"),
  HOLDING_CELL("Holding Cell"),
  MEDICAL("Medical"),

  GROUP("Group"),
  OFFICE("Other"),
  ADMINISTRATION_AREA("Administration Area"),
  BOOTH("Booth"),
  BOX("Box"),
  RETURN_TO_UNIT("Return to Unit"),
  CLASSROOM("Classroom"),
  TRAINING_AREA("Training Area"),
  TRAINING_ROOM("Training Room"),
  EXERCISE_AREA("Exercise Area"),
  AREA("Area"),
  SPORTS("Sports"),
  WORKSHOP("Workshop"),
  INSIDE_PARTY("Inside Party"),
  OUTSIDE_PARTY("Outside Party"),

  FAITH_AREA("Faith Area"),

  ADJUDICATION_ROOM("Adjudication Room"),
  APPOINTMENTS("Appointments"),
  VISITS("Visits"),
  VIDEO_LINK("Video Link"),
  ASSOCIATION("Association"),

  INTERNAL_GROUNDS("Internal Grounds"),
  INTERVIEW("Interview"),
  LOCATION("Location"),

  POSITION("Position"),
  SHELF("Shelf"),
  STORE("Store"),
  TABLE("Table"),
}

enum class DeactivatedReason(
  val description: String,
) {
  NEW_BUILDING("New Building"),
  CELL_RECLAIMS("Cell Reclaims"),
  CHANGE_OF_USE("Change of Use"),
  REFURBISHMENT("Refurbishment"),
  CLOSURE("Closure"),
  OTHER("Other"),
  LOCAL_WORK("Local Work"),
  STAFF_SHORTAGE("Staff Shortage"),
  MOTHBALLED("Mothballed"),
  DAMAGED("Damaged"),
  OUT_OF_USE("Out of Use"),
  CELLS_RETURNING_TO_USE("Cells Returning to Use"),
}

fun permanentlyInactiveReasons() = listOf(DeactivatedReason.CLOSURE, DeactivatedReason.MOTHBALLED, DeactivatedReason.CHANGE_OF_USE, DeactivatedReason.NEW_BUILDING)
