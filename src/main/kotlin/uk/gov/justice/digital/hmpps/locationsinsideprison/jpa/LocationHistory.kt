package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class LocationHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  val location: Location,

  @Enumerated(EnumType.STRING)
  val attributeName: LocationAttribute,

  val oldValue: String? = null,

  val newValue: String? = null,

  val amendedBy: String,

  val amendedDate: LocalDateTime,

)

enum class LocationAttribute(
  val description: String,
) {
  CODE("Code"),
  LOCATION_TYPE("Location Type"),
  RESIDENTIAL_HOUSING_TYPE("Residential Housing Type"),
  OPERATIONAL_CAPACITY("Operational Capacity"),
  CAPACITY("Capacity"),
  CERTIFIED_CAPACITY("Certified Capacity"),
  CERTIFIED("Certified"),
  PARENT_LOCATION("Parent Location"),
  ORDER_WITHIN_PARENT_LOCATION("Order Within Parent Location"),
  COMMENTS("Comments"),
  DESCRIPTION("Description"),
  ATTRIBUTES("Attributes"),
  USAGE("Usage"),
  ACTIVE("Active"),
  DEACTIVATED_DATE("Deactivated Date"),
  DEACTIVATED_REASON("Deactivated Reason"),
  PROPOSED_REACTIVATION_DATE("Proposed Reactivation Date"),
}
