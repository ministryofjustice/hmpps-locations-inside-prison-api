package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.Hibernate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ServiceUsingLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationConstants.CompoundConstant
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.LocationConstants.Constant

@Entity
class ServiceUsage(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  val location: Location,

  @Enumerated(EnumType.STRING)
  val serviceType: ServiceType,

) : Comparable<ServiceUsage> {

  companion object {
    private val COMPARATOR = compareBy<ServiceUsage>
      { it.location }
      .thenBy { it.serviceType.sequence }
  }

  override fun compareTo(other: ServiceUsage) = COMPARATOR.compare(this, other)

  fun toDto() = ServiceUsingLocationDto(
    serviceType = serviceType,
    serviceFamilyType = serviceType.serviceFamily,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as ServiceUsage

    if (location != other.location) return false
    if (serviceType.sequence != other.serviceType.sequence) return false
    if (serviceType != other.serviceType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + serviceType.sequence.hashCode()
    result = 31 * result + serviceType.hashCode()

    return result
  }
}

enum class ServiceFamilyType(
  val description: String,
  val sequence: Int = 99,
) {
  ACTIVITIES_APPOINTMENTS("Activities and appointments", 1),
  VIDEO_LINK_APPOINTMENTS("Video link hearings and appointments", 2),
  ADJUDICATIONS("Adjudications", 3),
  INTERNAL_MOVEMENTS("Internal movements", 4),
  OFFICIAL_VISITS("Official visits", 5),
  USE_OF_FORCE("Use of force", 6),
  ;

  fun toDto() = CompoundConstant(
    key = name,
    description = description,
    values = ServiceType.entries.sortedBy { it.sequence }
      .filter { it.serviceFamily == this }
      .map { Constant(key = it.name, description = it.description, additionalInformation = it.additionalInformation) },
  )
}

enum class ServiceType(
  val description: String,
  val nonResidentialUsageType: NonResidentialUsageType,
  val serviceFamily: ServiceFamilyType,
  val additionalInformation: String,
  val sequence: Int = 99,
  val nonResidentialLocationType: NonResidentialLocationType = NonResidentialLocationType.LOCATION,
) {
  APPOINTMENT(description = "Appointments", NonResidentialUsageType.APPOINTMENT, ServiceFamilyType.ACTIVITIES_APPOINTMENTS, additionalInformation = "For example a counselling session", sequence = 1),
  PROGRAMMES_AND_ACTIVITIES(description = "Programmes and activities", NonResidentialUsageType.PROGRAMMES_ACTIVITIES, ServiceFamilyType.ACTIVITIES_APPOINTMENTS, additionalInformation = "For example a workshop or lesson", sequence = 2),
  VIDEO_LINK(description = "Video link appointment/hearing", NonResidentialUsageType.OCCURRENCE, ServiceFamilyType.VIDEO_LINK_APPOINTMENTS, additionalInformation = "For example, a video link to a court hearing", sequence = 3, nonResidentialLocationType = NonResidentialLocationType.VIDEO_LINK),
  HEARING_LOCATION(description = "Adjudications - hearing location", NonResidentialUsageType.ADJUDICATION_HEARING, ServiceFamilyType.ADJUDICATIONS, additionalInformation = "For adjudication hearings", sequence = 4, nonResidentialLocationType = NonResidentialLocationType.ADJUDICATION_ROOM),
  LOCATION_OF_INCIDENT(description = "Adjudications - location of incident", NonResidentialUsageType.OCCURRENCE, ServiceFamilyType.ADJUDICATIONS, additionalInformation = "For example a location where an occurrence led to an adjudication hearing", sequence = 5),
  INTERNAL_MOVEMENTS(description = "Internal movements", NonResidentialUsageType.MOVEMENT, ServiceFamilyType.INTERNAL_MOVEMENTS, additionalInformation = "To record the location of unlocked prisoners within this establishment", sequence = 6),
  OFFICIAL_VISITS(description = "Official visits", NonResidentialUsageType.VISIT, ServiceFamilyType.OFFICIAL_VISITS, additionalInformation = "For example, arranging a face to face visit with a solicitor", sequence = 7),
  USE_OF_FORCE(description = "Use of force", NonResidentialUsageType.OCCURRENCE, ServiceFamilyType.USE_OF_FORCE, additionalInformation = "To report where a use of force incident took place", sequence = 8),
}
