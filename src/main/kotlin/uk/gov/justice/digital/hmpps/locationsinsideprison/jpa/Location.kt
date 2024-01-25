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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorFormula("case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' else 'RESIDENTIAL' end")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Location(
  @Id
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  @Column(name = "id", updatable = false, nullable = false)
  val id: UUID? = null,

  var code: String,

  var pathHierarchy: String,

  @Enumerated(EnumType.STRING)
  var locationType: LocationType,

  val prisonId: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  @JoinColumn(name = "parent_id")
  protected var parent: Location? = null,

  var description: String? = null,

  var comments: String? = null,

  var orderWithinParentLocation: Int? = null,

  var active: Boolean = true,
  var deactivatedDate: LocalDate? = null,
  @Enumerated(EnumType.STRING)
  var deactivatedReason: DeactivatedReason? = null,
  var reactivatedDate: LocalDate? = null,

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  protected var childLocations: MutableList<Location> = mutableListOf(),

  val whenCreated: LocalDateTime,
  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) : Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun setLocationCode(code: String) {
    this.code = code
    updateHierarchicalPath()
  }

  fun getLocationPathHierarchy(): String {
    return pathHierarchy
  }

  fun setLocationParent(parent: Location) {
    removeParent()
    parent.addChildLocation(this)
  }

  private fun removeParent() {
    parent?.removeChildLocation(this)
    parent = null
  }

  fun getLocationCode(): String {
    return code
  }

  fun getLocationParent(): Location? {
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

  open fun toDto(includeChildren: Boolean = false): LocationDto {
    return LocationDto(
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
      childLocations = if (includeChildren) childLocations.map { it.toDto(true) } else null,
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    if (prisonId != other.prisonId) return false
    if (pathHierarchy != other.pathHierarchy) return false

    return true
  }

  override fun hashCode(): Int {
    var result = prisonId.hashCode()
    result = 31 * result + pathHierarchy.hashCode()
    return result
  }

  open fun updateWith(patch: PatchLocationRequest, updatedBy: String, clock: Clock): Location {
    setLocationCode(patch.code ?: this.getLocationCode())
    this.locationType = patch.locationType ?: this.locationType
    this.description = patch.description ?: this.description
    this.comments = patch.comments ?: this.comments
    this.orderWithinParentLocation = patch.orderWithinParentLocation ?: this.orderWithinParentLocation
    this.updatedBy = updatedBy
    this.whenUpdated = LocalDateTime.now(clock)

    return this
  }

  fun deactivate(deactivatedReason: DeactivatedReason, userOrSystemInContext: String, clock: Clock) {
    this.active = false
    this.deactivatedReason = deactivatedReason
    this.deactivatedDate = LocalDate.now(clock)
    this.reactivatedDate = null
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  fun reactivate(userOrSystemInContext: String, clock: Clock) {
    this.active = true
    this.deactivatedReason = null
    this.deactivatedDate = null
    this.reactivatedDate = LocalDate.now(clock)
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }
}

enum class LocationType(
  val description: String,
) {
  WING("Wing"),
  LANDING("Landing"),
  CELL("Cell"),
  SPUR("Spur"),
  OFFICE("Other"),
  ADMINISTRATION_AREA("Administration Area"),
  RESIDENTIAL_UNIT("Residential Unit"),
  MEDICAL("Medical"),
  HOLDING_CELL("Holding Cell"),
  HOLDING_AREA("Holding Area"),
  BOOTH("Booth"),
  BOX("Box"),
  ROOM("Room"),
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
  EXTERNAL_GROUNDS("External Grounds"),
  GROUP("Group"),
  INTERNAL_GROUNDS("Internal Grounds"),
  INTERVIEW("Interview"),
  LOCATION("NonResidentialLocation"),
  MOVEMENT_AREA("Movement Area"),
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

enum class ResidentialHousingType(
  val description: String,
) {
  HEALTHCARE("Healthcare"),
  HOLDING_CELL("Holding Cell"),
  NORMAL_ACCOMMODATION("Normal Accommodation"),
  OTHER_USE("Other Use"),
  RECEPTION("Reception"),
  SEGREGATION("Segregation"),
  SPECIALIST_CELL("Specialist Cell"),
}
