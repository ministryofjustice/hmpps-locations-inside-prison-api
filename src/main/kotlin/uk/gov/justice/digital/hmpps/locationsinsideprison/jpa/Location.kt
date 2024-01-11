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
import org.hibernate.Hibernate
import org.hibernate.annotations.GenericGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
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

  val code: String,

  @Enumerated(EnumType.STRING)
  var locationType: LocationType,

  var description: String,

  val prisonId: String,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  @JoinColumn(name = "parent_id")
  var parent: Location? = null,

  val active: Boolean = true,

  @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  var childLocations: MutableList<Location> = mutableListOf(),

  var whenCreated: LocalDateTime,
  var whenUpdated: LocalDateTime,
  var updatedBy: String,
) : Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun addChildLocation(location: Location): Location {
    location.parent = this
    childLocations.add(location)
    return this
  }

  fun findTopLevelLocation(): Location {
    fun up(location: Location): Location {
      return if (location.parent == null) {
        location
      } else {
        up(location.parent!!)
      }
    }
    return up(this)
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

  fun toDto(): LocationDTO {
    return LocationDTO(
      id = id!!,
      code = code,
      locationType = locationType,
      description = description,
      prisonId = prisonId,
      parentId = parent?.id,
      topLevelId = findTopLevelLocation().id!!,
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
  WING, HOUSEBLOCK, LANDING, CELL, COURT, HOSPITAL, VISIT, OFFSITE, OTHER
}
