package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import org.hibernate.Hibernate

@Entity
class LocationAttribute(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  val location: Location,
  @Enumerated(EnumType.STRING)
  @Column(name = "usage_type", nullable = false)
  val type: LocationAttributeType,

  @Enumerated(EnumType.STRING)
  @Column(name = "usage_value", nullable = false)
  val value: LocationAttributeValue,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as LocationAttribute

    if (location != other.location) return false
    if (type != other.type) return false
    if (value != other.value) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + type.hashCode()
    result = 31 * result + value.hashCode()
    return result
  }
}

enum class LocationAttributeType(
  val description: String,
) {
  USED_FOR("Used for"),
  LOCATION_ATTRIBUTE("NonResidentialLocation attribute"),
  SANITATION_FITTINGS("Sanitation and fittings"),
  NON_ASSOCIATIONS("Non-associations"),
  SECURITY("Supervision and Security"),
}

enum class LocationAttributeValue(
  val type: LocationAttributeType,
  val description: String,
) {
  ABD(LocationAttributeType.SANITATION_FITTINGS, "Anti Barricade Door"),
  ACB(LocationAttributeType.SANITATION_FITTINGS, "Auditable Cell Bell"),
  FIB(LocationAttributeType.SANITATION_FITTINGS, "Fixed Bed"),
  MD(LocationAttributeType.SANITATION_FITTINGS, "Metal Door"),
  MOB(LocationAttributeType.SANITATION_FITTINGS, "Movable Bed"),
  PC(LocationAttributeType.SANITATION_FITTINGS, "Privacy Curtain"),
  PS(LocationAttributeType.SANITATION_FITTINGS, "Privacy Screen"),
  SCB(LocationAttributeType.SANITATION_FITTINGS, "Standard Cell Bell"),
  SETO(LocationAttributeType.SANITATION_FITTINGS, "Separate Toilet"),
  WD(LocationAttributeType.SANITATION_FITTINGS, "Wooden Door"),

  A(LocationAttributeType.LOCATION_ATTRIBUTE, "Cat A Cell"),
  DO(LocationAttributeType.LOCATION_ATTRIBUTE, "Double Occupancy"),
  ELC(LocationAttributeType.LOCATION_ATTRIBUTE, "E List Cell"),
  GC(LocationAttributeType.LOCATION_ATTRIBUTE, "Gated Cell"),
  LC(LocationAttributeType.LOCATION_ATTRIBUTE, "Listener Cell"),
  LF(LocationAttributeType.LOCATION_ATTRIBUTE, "Locate Flat"),
  MO(LocationAttributeType.LOCATION_ATTRIBUTE, "Multiple Occupancy"),
  NSMC(LocationAttributeType.LOCATION_ATTRIBUTE, "Non Smoker Cell"),
  OC(LocationAttributeType.LOCATION_ATTRIBUTE, "Observation Cell"),
  SC(LocationAttributeType.LOCATION_ATTRIBUTE, "Safe Cell"),
  SO(LocationAttributeType.LOCATION_ATTRIBUTE, "Single Occupancy"),
  SPC(LocationAttributeType.LOCATION_ATTRIBUTE, "Special Cell"),
  WA(LocationAttributeType.LOCATION_ATTRIBUTE, "Wheelchair Access"),

  UF_1(LocationAttributeType.USED_FOR, "Unconvicted Juveniles"),
  UF_2(LocationAttributeType.USED_FOR, "Sentenced Juveniles"),
  UF_3(LocationAttributeType.USED_FOR, "Unconvicted 18 to 20 year olds"),
  UF_4(LocationAttributeType.USED_FOR, "Sentenced 18 to 20 year olds"),
  UF_5(LocationAttributeType.USED_FOR, "Unconvicted Adults"),
  UF_6(LocationAttributeType.USED_FOR, "Sentenced Adults"),
  UF_7(LocationAttributeType.USED_FOR, "Vulnerable Prisoner Unit"),
  UF_8(LocationAttributeType.USED_FOR, "Special Unit"),
  UF_9(LocationAttributeType.USED_FOR, "Resettlement Hostel"),
  UF_10(LocationAttributeType.USED_FOR, "Healthcare Centre"),
  UF_11(LocationAttributeType.USED_FOR, "National Resource Hospital"),
  UF_12(LocationAttributeType.USED_FOR, "Other (Please Specify)"),
  UF_A(LocationAttributeType.USED_FOR, "Remand Centre"),
  UF_B(LocationAttributeType.USED_FOR, "Local Prison"),
  UF_C(LocationAttributeType.USED_FOR, "Closed Prison"),
  UF_D(LocationAttributeType.USED_FOR, "Open Training"),
  UF_E(LocationAttributeType.USED_FOR, "Hostel"),
  UF_H(LocationAttributeType.USED_FOR, "National Resource Hospital"),
  UF_I(LocationAttributeType.USED_FOR, "Closed Young Offender Institute"),
  UF_J(LocationAttributeType.USED_FOR, "Open Young Offender Institute"),
  UF_K(LocationAttributeType.USED_FOR, "Remand Institute for Under 18s"),
  UF_L(LocationAttributeType.USED_FOR, "Institution for Sentenced Under 18s"),
  UF_R(LocationAttributeType.USED_FOR, "ECL Component Code"),
  UF_S(LocationAttributeType.USED_FOR, "Special Unit"),
  UF_T(LocationAttributeType.USED_FOR, "Additional Special Unit"),
  UF_V(LocationAttributeType.USED_FOR, "Vulnerable Prisoner Unit"),
  UF_Y(LocationAttributeType.USED_FOR, "Second Closed Trainer/Single LIDS Site"),
  UF_Z(LocationAttributeType.USED_FOR, "Immigration Detainees"),

  CELL(LocationAttributeType.NON_ASSOCIATIONS, "Do Not Locate in Same Cell"),
  LANDING(LocationAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Landing"),
  WING(LocationAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Wing"),

  CAT_A(LocationAttributeType.SECURITY, "Cat A"),
  CAT_B(LocationAttributeType.SECURITY, "Cat B"),
  CAT_C(LocationAttributeType.SECURITY, "Cat C"),
  CAT_D(LocationAttributeType.SECURITY, "Cat D"),
  CAT_E(LocationAttributeType.SECURITY, "Cat A Ex"),
  EL(LocationAttributeType.SECURITY, "Eligible"),
  GRANTED(LocationAttributeType.SECURITY, "Parole Granted"),
  CAT_H(LocationAttributeType.SECURITY, "Cat A Hi"),
  HI(LocationAttributeType.SECURITY, "High"),
  I(LocationAttributeType.SECURITY, "YOI Closed"),
  INEL(LocationAttributeType.SECURITY, "Ineligible"),
  J(LocationAttributeType.SECURITY, "YOI Open"),
  K(LocationAttributeType.SECURITY, "YOI Short Sentence"),
  L(LocationAttributeType.SECURITY, "YOI Long-Term Closed"),
  LOW(LocationAttributeType.SECURITY, "Low"),
  MED(LocationAttributeType.SECURITY, "Medium"),
  N(LocationAttributeType.SECURITY, "No"),
  NA(LocationAttributeType.SECURITY, "Not Applicable"),
  P(LocationAttributeType.SECURITY, "Prov A"),
  PEND(LocationAttributeType.SECURITY, "Pending"),
  CAT_Q(LocationAttributeType.SECURITY, "Female Restricted"),
  CAT_R(LocationAttributeType.SECURITY, "Female Closed"),
  REF_REVIEW(LocationAttributeType.SECURITY, "Ref / Review - Date set for Review"),
  REFUSED(LocationAttributeType.SECURITY, "Refused - No Review possible"),
  S(LocationAttributeType.SECURITY, "Female Semi"),
  STANDARD(LocationAttributeType.SECURITY, "Standard"),
  CAT_T(LocationAttributeType.SECURITY, "Female Open"),
  CAT_U(LocationAttributeType.SECURITY, "Unsentenced"),
  V(LocationAttributeType.SECURITY, "YOI Restricted"),
  CAT_X(LocationAttributeType.SECURITY, "Uncategorised Sentenced Male"),
  Y(LocationAttributeType.SECURITY, "Yes"),
  Z(LocationAttributeType.SECURITY, "Unclass"),
}
