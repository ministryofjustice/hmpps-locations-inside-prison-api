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

  fun isInternalMovementTracking() = serviceType == ServiceType.INTERNAL_MOVEMENTS

  fun toDto() = ServiceUsingLocationDto(
    serviceType = serviceType,
    serviceName = serviceType.description,
    usageType = serviceType.nonResidentialUsageType,
    serviceFamilyType = serviceType.serviceFamily,
    serviceFamilyName = serviceType.serviceFamily?.description,
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
) {
  ACTIVITIES_APPOINTMENTS("Activities and appointments"),
  ADJUDICATIONS("Adjudications"),
}

enum class ServiceType(
  val description: String,
  val nonResidentialUsageType: NonResidentialUsageType,
  val serviceFamily: ServiceFamilyType? = null,
  val sequence: Int = 99,
) {
  APPOINTMENT("Appointments", NonResidentialUsageType.APPOINTMENT, ServiceFamilyType.ACTIVITIES_APPOINTMENTS, 1),
  PROGRAMMES_AND_ACTIVITIES("Programmes and activities", NonResidentialUsageType.PROGRAMMES_ACTIVITIES, ServiceFamilyType.ACTIVITIES_APPOINTMENTS, 2),
  HEARING_LOCATION("Hearing location", NonResidentialUsageType.ADJUDICATION_HEARING, ServiceFamilyType.ADJUDICATIONS, 3),
  LOCATION_OF_INCIDENT("Location of incident", NonResidentialUsageType.OCCURRENCE, ServiceFamilyType.ADJUDICATIONS, 4),
  INTERNAL_MOVEMENTS("Internal movements", NonResidentialUsageType.MOVEMENT, sequence = 5),
  OFFICIAL_VISITS("Official visits", NonResidentialUsageType.VISIT, sequence = 6),
  USE_OF_FORCE("Use of force", NonResidentialUsageType.OCCURRENCE, sequence = 7),
}
