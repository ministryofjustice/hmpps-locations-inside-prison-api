package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import org.hibernate.annotations.GenericGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDTO

@Entity
class Location(
  @Id
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  private var code: String,

  private var pathHierarchy: String,

  @Enumerated(EnumType.STRING)
  var locationType: LocationType,

  val prisonId: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  @JoinColumn(name = "parent_id")
  private var parent: Location? = null,

  val description: String? = null,

  val comments: String? = null,

  val orderWithinParentLocation: Int? = null,

  val active: Boolean = true,
  var deactivatedDate: LocalDate? = null,
  var deactivatedReason: DeactivatedReason? = null,
  var reactivatedDate: LocalDate? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
  val capacity: Capacity? = null,

  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], optional = true)
  val certification: Certification? = null,

  var residentialHousingType: ResidentialHousingType? = null,

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  private var childLocations: MutableList<Location> = mutableListOf(),

  val whenCreated: LocalDateTime,
  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) : Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun setCode(code: String) {
    this.code = code
    updateHierarchicalPath()
  }

  fun getPathHierarchy(): String {
    return pathHierarchy
  }

  fun setParent(parent: Location) {
    removeParent()
    parent.addChildLocation(this)
  }

  private fun removeParent() {
    parent?.removeChildLocation(this)
    parent = null
  }

  fun getCode(): String {
    return code
  }

  fun getParent(): Location? {
    return parent
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
    return parent?.findTopLevelLocation() ?: this
  }

  private fun updateHierarchicalPath() {
    pathHierarchy = getHierarchicalPath()
    for (childLocation in childLocations) {
      childLocation.updateHierarchicalPath()
    }
  }

  private fun getHierarchicalPath(): String {
    return if (parent == null) {
      code
    } else {
      "${parent!!.getHierarchicalPath()}-$code"
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

  fun toDto(includeChildren: Boolean = false): LocationDTO {
    return LocationDTO(
      id = id!!,
      code = code,
      locationType = locationType,
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = parent?.id,
      topLevelId = findTopLevelLocation().id!!,
      description = description,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = active,
      deactivatedDate = deactivatedDate,
      deactivatedReason = deactivatedReason,
      reactivatedDate = reactivatedDate,
      capacity = capacity?.capacity,
      operationalCapacity = capacity?.operationalCapacity,
      currentOccupancy = capacity?.currentOccupancy,
      certified = certification?.certified,
      capacityOfCertifiedCell = certification?.capacityOfCertifiedCell,
      residentialHousingType = residentialHousingType,
      childLocations = if (includeChildren) childLocations.map { it.toDto(true) } else null,
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    if (code != other.code) return false
    if (prisonId != other.prisonId) return false

    return true
  }

  override fun hashCode(): Int {
    var result = code.hashCode()
    result = 31 * result + prisonId.hashCode()
    return result
  }
}

enum class LocationType {
  WING, LANDING, CELL, SPUR,
  OFFICE, ADMINISTRATION_AREA, RESIDENTIAL_UNIT, MEDICAL,
  HOLDING_CELL, HOLDING_AREA, BOOTH, BOX,

  CLASSROOM, TRAINING_AREA, TRAINING_ROOM,
  EXERCISE_AREA, SPORTS, WORKSHOP,
  INSIDE_PARTY, OUTSIDE_PARTY,

  FAITH_AREA,

  ADJUDICATION_ROOM, APPOINTMENTS, AREA, ASSOCIATION,
  EXTERNAL_GROUNDS, GROUP, INTERNAL_GROUNDS, INTERVIEW,
  LOCATION, MOVEMENT_AREA, POSITION, RETURN_TO_UNIT,
  SHELF, STORE, TABLE, VIDEO_LINK, VISITS
}

enum class DeactivatedReason {
  NEW_BUILDING,
  CELL_RECLAIMS,
  CHANGE_OF_USE,
  REFURBISHMENT,
  CLOSURE,
  OTHER,
  LOCAL_WORK,
  STAFF_SHORTAGE,
  MOTHBALLED,
  DAMAGED,
  OUT_OF_USE,
  CELLS_RETURNING_TO_USE,
}

enum class ResidentialHousingType {
  HEALTHCARE,
  HOLDING_CELL,
  NORMAL_ACCOMMODATION,
  OTHER_USE,
  RECEPTION,
  SEGREGATION,
  SPECIALIST_CELL,
}
