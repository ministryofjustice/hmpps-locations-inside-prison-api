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

@Entity
class ResidentialAttribute(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
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

  override fun toString(): String {
    return "ResidentialAttribute(attributeType=$attributeType, attributeValue=$attributeValue)"
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
  val mapTo: SpecialistCellType? = null,
) {
  ANTI_BARRICADE_DOOR(ResidentialAttributeType.SANITATION_FITTINGS, "Anti Barricade Door"),
  AUDITABLE_CELL_BELL(ResidentialAttributeType.SANITATION_FITTINGS, "Auditable Cell Bell"),
  FIXED_BED(ResidentialAttributeType.SANITATION_FITTINGS, "Fixed Bed"),
  METAL_DOOR(ResidentialAttributeType.SANITATION_FITTINGS, "Metal Door"),
  MOVABLE_BED(ResidentialAttributeType.SANITATION_FITTINGS, "Movable Bed"),
  PRIVACY_CURTAIN(ResidentialAttributeType.SANITATION_FITTINGS, "Privacy Curtain"),
  PRIVACY_SCREEN(ResidentialAttributeType.SANITATION_FITTINGS, "Privacy Screen"),
  STANDARD_CELL_BELL(ResidentialAttributeType.SANITATION_FITTINGS, "Standard Cell Bell"),
  SEPARATE_TOILET(ResidentialAttributeType.SANITATION_FITTINGS, "Separate Toilet"),
  WOODEN_DOOR(ResidentialAttributeType.SANITATION_FITTINGS, "Wooden Door"),

  CAT_A_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Cat A Cell", SpecialistCellType.CAT_A),
  DOUBLE_OCCUPANCY(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Double Occupancy"),
  E_LIST_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "E List Cell", SpecialistCellType.ESCAPE_LIST),
  GATED_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Gated Cell", SpecialistCellType.CONSTANT_SUPERVISION),
  LISTENER_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Listener Cell", SpecialistCellType.LISTENER_CRISIS),
  LOCATE_FLAT(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Locate Flat", SpecialistCellType.LOCATE_FLAT_CELL),
  MULTIPLE_OCCUPANCY(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Multiple Occupancy"),
  NON_SMOKER_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Non Smoker Cell"),
  OBSERVATION_CELL(
    ResidentialAttributeType.LOCATION_ATTRIBUTE,
    "Observation Cell",
    SpecialistCellType.CONSTANT_SUPERVISION,
  ),
  SAFE_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Safe Cell", SpecialistCellType.SAFE_CELL),
  SINGLE_OCCUPANCY(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Single Occupancy"),
  SPECIAL_CELL(ResidentialAttributeType.LOCATION_ATTRIBUTE, "Special Cell"),
  WHEELCHAIR_ACCESS(
    ResidentialAttributeType.LOCATION_ATTRIBUTE,
    "Wheelchair Access",
    SpecialistCellType.WHEELCHAIR_ACCESSIBLE,
  ),

  UNCONVICTED_JUVENILES(ResidentialAttributeType.USED_FOR, "Unconvicted Juveniles"),
  SENTENCED_JUVENILES(ResidentialAttributeType.USED_FOR, "Sentenced Juveniles"),
  UNCONVICTED_18_20(ResidentialAttributeType.USED_FOR, "Unconvicted 18 to 20 year olds"),
  SENTENCED_18_20(ResidentialAttributeType.USED_FOR, "Sentenced 18 to 20 year olds"),
  UNCONVICTED_ADULTS(ResidentialAttributeType.USED_FOR, "Unconvicted Adults"),
  SENTENCED_ADULTS(ResidentialAttributeType.USED_FOR, "Sentenced Adults"),
  VULNERABLE_PRISONER_UNIT(ResidentialAttributeType.USED_FOR, "Vulnerable Prisoner Unit"),
  SPECIAL_UNIT(ResidentialAttributeType.USED_FOR, "Special Unit"),
  RESETTLEMENT_HOSTEL(ResidentialAttributeType.USED_FOR, "Resettlement Hostel"),
  HEALTHCARE_CENTRE(ResidentialAttributeType.USED_FOR, "Healthcare Centre"),
  NATIONAL_RESOURCE_HOSPITAL(ResidentialAttributeType.USED_FOR, "National Resource Hospital"),
  OTHER_SPECIFIED(ResidentialAttributeType.USED_FOR, "Other (Please Specify)"),
  REMAND_CENTRE(ResidentialAttributeType.USED_FOR, "Remand Centre"),
  LOCAL_PRISON(ResidentialAttributeType.USED_FOR, "Local Prison"),
  CLOSED_PRISON(ResidentialAttributeType.USED_FOR, "Closed Prison"),
  OPEN_TRAINING(ResidentialAttributeType.USED_FOR, "Open Training"),
  HOSTEL(ResidentialAttributeType.USED_FOR, "Hostel"),
  CLOSED_YOUNG_OFFENDER(ResidentialAttributeType.USED_FOR, "Closed Young Offender Institute"),
  OPEN_YOUNG_OFFENDER(ResidentialAttributeType.USED_FOR, "Open Young Offender Institute"),
  REMAND_UNDER_18(ResidentialAttributeType.USED_FOR, "Remand Institute for Under 18s"),
  SENTENCED_UNDER_18(ResidentialAttributeType.USED_FOR, "Institution for Sentenced Under 18s"),
  ECL_COMPONENT(ResidentialAttributeType.USED_FOR, "ECL Component Code"),
  ADDITIONAL_SPECIAL_UNIT(ResidentialAttributeType.USED_FOR, "Additional Special Unit"),
  SECOND_CLOSED_TRAINER(ResidentialAttributeType.USED_FOR, "Second Closed Trainer/Single LIDS Site"),
  IMMIGRATION_DETAINEES(ResidentialAttributeType.USED_FOR, "Immigration Detainees"),

  CELL(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate in Same Cell"),
  LANDING(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Landing"),
  WING(ResidentialAttributeType.NON_ASSOCIATIONS, "Do Not Locate on Same Wing"),

  CAT_A(ResidentialAttributeType.SECURITY, "Cat A"),
  CAT_A_EX(ResidentialAttributeType.SECURITY, "Cat A Ex"),
  CAT_A_HI(ResidentialAttributeType.SECURITY, "Cat A Hi"),
  CAT_B(ResidentialAttributeType.SECURITY, "Cat B"),
  CAT_C(ResidentialAttributeType.SECURITY, "Cat C"),
  CAT_D(ResidentialAttributeType.SECURITY, "Cat D"),
  ELIGIBLE(ResidentialAttributeType.SECURITY, "Eligible"),
  PAROLE_GRANTED(ResidentialAttributeType.SECURITY, "Parole Granted"),
  INELIGIBLE(ResidentialAttributeType.SECURITY, "Ineligible"),
  YOI_CLOSED(ResidentialAttributeType.SECURITY, "YOI Closed"),
  YOI_OPEN(ResidentialAttributeType.SECURITY, "YOI Open"),
  YOI_RESTRICTED(ResidentialAttributeType.SECURITY, "YOI Restricted"),
  YOI_SHORT_SENTENCE(ResidentialAttributeType.SECURITY, "YOI Short Sentence"),
  YOI_LONG_TERM_CLOSED(ResidentialAttributeType.SECURITY, "YOI Long-Term Closed"),
  UNCLASSIFIED(ResidentialAttributeType.SECURITY, "Un-classified"),
  UNCATEGORISED_SENTENCED_MALE(ResidentialAttributeType.SECURITY, "Un-categorised Sentenced Male"),
  LOW(ResidentialAttributeType.SECURITY, "Low"),
  MEDIUM(ResidentialAttributeType.SECURITY, "Medium"),
  HIGH(ResidentialAttributeType.SECURITY, "High"),
  NOT_APPLICABLE(ResidentialAttributeType.SECURITY, "Not Applicable"),
  PROV_A(ResidentialAttributeType.SECURITY, "Prov A"),
  PENDING(ResidentialAttributeType.SECURITY, "Pending"),
  REF_REVIEW(ResidentialAttributeType.SECURITY, "Ref / Review - Date set for Review"),
  REFUSED_NO_REVIEW(ResidentialAttributeType.SECURITY, "Refused - No Review possible"),
  STANDARD(ResidentialAttributeType.SECURITY, "Standard"),
  FEMALE_RESTRICTED(ResidentialAttributeType.SECURITY, "Female Restricted"),
  FEMALE_CLOSED(ResidentialAttributeType.SECURITY, "Female Closed"),
  FEMALE_SEMI(ResidentialAttributeType.SECURITY, "Female Semi"),
  FEMALE_OPEN(ResidentialAttributeType.SECURITY, "Female Open"),
  UN_SENTENCED(ResidentialAttributeType.SECURITY, "Un-sentenced"),
  YES(ResidentialAttributeType.SECURITY, "Yes"),
  NO(ResidentialAttributeType.SECURITY, "No"),
}
