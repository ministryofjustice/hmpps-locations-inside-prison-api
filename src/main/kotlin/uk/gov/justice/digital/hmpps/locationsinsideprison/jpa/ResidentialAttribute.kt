package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import jakarta.persistence.CascadeType
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
class ResidentialAttribute(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
  val location: Location,
  @Enumerated(EnumType.STRING)
  val attributeType: ResidentialAttributeType,

  @Enumerated(EnumType.STRING)
  val attributeValue: ResidentialAttributeValue,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as ResidentialAttribute

    if (location != other.location) return false
    if (attributeType != other.attributeType) return false
    if (attributeValue != other.attributeValue) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + attributeType.hashCode()
    result = 31 * result + attributeValue.hashCode()
    return result
  }
}

enum class ResidentialAttributeType(
  val description: String,
) {
  USED_FOR("Used for"),
  LOCATION_ATTRIBUTE("Location attribute"),
  SANITATION_FITTINGS("Sanitation and fittings"),
  NON_ASSOCIATIONS("Non-associations"),
  SECURITY("Supervision and Security"),
}

enum class ResidentialAttributeValue(
  val type: ResidentialAttributeType,
  val description: String,
) {
  ABD(ResidentialAttributeType.SANITATION_FITTINGS, "Anti Barricade Door"),
  ACB(ResidentialAttributeType.SANITATION_FITTINGS, "Auditable Cell Bell"),
  FIB(ResidentialAttributeType.SANITATION_FITTINGS, "Fixed Bed"),
  MD(ResidentialAttributeType.SANITATION_FITTINGS, "Metal Door"),
  MOB(ResidentialAttributeType.SANITATION_FITTINGS, "Movable Bed"),
  PC(ResidentialAttributeType.SANITATION_FITTINGS, "Privacy Curtain"),
  PS(ResidentialAttributeType.SANITATION_FITTINGS, "Privacy Screen"),
  SCB(ResidentialAttributeType.SANITATION_FITTINGS, "Standard Cell Bell"),
  SETO(ResidentialAttributeType.SANITATION_FITTINGS, "Separate Toilet"),
  WD(ResidentialAttributeType.SANITATION_FITTINGS, "Wooden Door"),

  A(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Cat A Cell"),
  DO(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Double Occupancy"),
  ELC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "E List Cell"),
  GC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Gated Cell"),
  LC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Listener Cell"),
  LF(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Locate Flat"),
  MO(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Multiple Occupancy"),
  NSMC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Non Smoker Cell"),
  OC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Observation Cell"),
  SC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Safe Cell"),
  SO(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Single Occupancy"),
  SPC(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Special Cell"),
  WA(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Wheelchair Access"),

  UF_1(ResidentialAttributeType.USED_FOR, "Unconvicted Juveniles"),
  UF_2(ResidentialAttributeType.USED_FOR, "Sentenced Juveniles"),
  UF_3(ResidentialAttributeType.USED_FOR, "Unconvicted 18 to 20 year olds"),
  UF_4(ResidentialAttributeType.USED_FOR, "Sentenced 18 to 20 year olds"),
  UF_5(ResidentialAttributeType.USED_FOR, "Unconvicted Adults"),
  UF_6(ResidentialAttributeType.USED_FOR, "Sentenced Adults"),
  UF_7(ResidentialAttributeType.USED_FOR, "Vulnerable Prisoner Unit"),
  UF_8(ResidentialAttributeType.USED_FOR, "Special Unit"),
  UF_9(ResidentialAttributeType.USED_FOR, "Resettlement Hostel"),
  UF_10(ResidentialAttributeType.USED_FOR, "Healthcare Centre"),
  UF_11(ResidentialAttributeType.USED_FOR, "National Resource Hospital"),
  UF_12(ResidentialAttributeType.USED_FOR, "Other (Please Specify)"),
  UF_A(ResidentialAttributeType.USED_FOR, "Remand Centre"),
  UF_B(ResidentialAttributeType.USED_FOR, "Local Prison"),
  UF_C(ResidentialAttributeType.USED_FOR, "Closed Prison"),
  UF_D(ResidentialAttributeType.USED_FOR, "Open Training"),
  UF_E(ResidentialAttributeType.USED_FOR, "Hostel"),
  UF_H(ResidentialAttributeType.USED_FOR, "National Resource Hospital"),
  UF_I(ResidentialAttributeType.USED_FOR, "Closed Young Offender Institute"),
  UF_J(ResidentialAttributeType.USED_FOR, "Open Young Offender Institute"),
  UF_K(ResidentialAttributeType.USED_FOR, "Remand Institute for Under 18s"),
  UF_L(ResidentialAttributeType.USED_FOR, "Institution for Sentenced Under 18s"),
  UF_R(ResidentialAttributeType.USED_FOR, "ECL Component Code"),
  UF_S(ResidentialAttributeType.USED_FOR, "Special Unit"),
  UF_T(ResidentialAttributeType.USED_FOR, "Additional Special Unit"),
  UF_V(ResidentialAttributeType.USED_FOR, "Vulnerable Prisoner Unit"),
  UF_Y(ResidentialAttributeType.USED_FOR, "Second Closed Trainer/Single LIDS Site"),
  UF_Z(ResidentialAttributeType.USED_FOR, "Immigration Detainees"),

  CELL(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate in Same Cell"),
  LANDING(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Landing"),
  WING(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Wing"),

  CAT_A(ResidentialAttributeType.SECURITY, "Cat A"),
  CAT_B(ResidentialAttributeType.SECURITY, "Cat B"),
  CAT_C(ResidentialAttributeType.SECURITY, "Cat C"),
  CAT_D(ResidentialAttributeType.SECURITY, "Cat D"),
  CAT_E(ResidentialAttributeType.SECURITY, "Cat A Ex"),
  EL(ResidentialAttributeType.SECURITY, "Eligible"),
  GRANTED(ResidentialAttributeType.SECURITY, "Parole Granted"),
  CAT_H(ResidentialAttributeType.SECURITY, "Cat A Hi"),
  HI(ResidentialAttributeType.SECURITY, "High"),
  I(ResidentialAttributeType.SECURITY, "YOI Closed"),
  INEL(ResidentialAttributeType.SECURITY, "Ineligible"),
  J(ResidentialAttributeType.SECURITY, "YOI Open"),
  K(ResidentialAttributeType.SECURITY, "YOI Short Sentence"),
  L(ResidentialAttributeType.SECURITY, "YOI Long-Term Closed"),
  LOW(ResidentialAttributeType.SECURITY, "Low"),
  MED(ResidentialAttributeType.SECURITY, "Medium"),
  N(ResidentialAttributeType.SECURITY, "No"),
  NA(ResidentialAttributeType.SECURITY, "Not Applicable"),
  P(ResidentialAttributeType.SECURITY, "Prov A"),
  PEND(ResidentialAttributeType.SECURITY, "Pending"),
  CAT_Q(ResidentialAttributeType.SECURITY, "Female Restricted"),
  CAT_R(ResidentialAttributeType.SECURITY, "Female Closed"),
  REF_REVIEW(ResidentialAttributeType.SECURITY, "Ref / Review - Date set for Review"),
  REFUSED(ResidentialAttributeType.SECURITY, "Refused - No Review possible"),
  S(ResidentialAttributeType.SECURITY, "Female Semi"),
  STANDARD(ResidentialAttributeType.SECURITY, "Standard"),
  CAT_T(ResidentialAttributeType.SECURITY, "Female Open"),
  CAT_U(ResidentialAttributeType.SECURITY, "Unsentenced"),
  V(ResidentialAttributeType.SECURITY, "YOI Restricted"),
  CAT_X(ResidentialAttributeType.SECURITY, "Uncategorised Sentenced Male"),
  Y(ResidentialAttributeType.SECURITY, "Yes"),
  Z(ResidentialAttributeType.SECURITY, "Unclass"),
}
