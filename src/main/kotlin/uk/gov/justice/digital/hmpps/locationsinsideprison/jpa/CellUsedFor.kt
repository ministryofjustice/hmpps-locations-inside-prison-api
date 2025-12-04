package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
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
class CellUsedFor(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  val location: Location,

  @Enumerated(EnumType.STRING)
  val usedFor: UsedForType,

) : Comparable<CellUsedFor> {

  companion object {
    private val COMPARATOR = compareBy<CellUsedFor>
      { it.location }
      .thenBy { it.usedFor }
  }

  override fun compareTo(other: CellUsedFor) = COMPARATOR.compare(this, other)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as CellUsedFor

    if (location != other.location) return false
    if (usedFor != other.usedFor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = location.hashCode()
    result = 31 * result + usedFor.hashCode()

    return result
  }
}

@Schema(description = "Used For Types")
@JsonInclude(JsonInclude.Include.NON_NULL)
enum class UsedForType(
  val description: String,
  val secureEstateOnly: Boolean = false,
  val femaleOnly: Boolean = false,
  val sequence: Int = 99,
) {
  CLOSE_SUPERVISION_CENTRE("Close Supervision Centre (CSC)", sequence = 1, secureEstateOnly = true),
  SUB_MISUSE_DRUG_RECOVERY("Drug recovery / Incentivised substance free living (ISFL)", sequence = 2),
  FIRST_NIGHT_CENTRE("First night centre / Induction", sequence = 3),
  HIGH_SECURITY("High security unit", sequence = 4, secureEstateOnly = true),
  IPP_LONG_TERM_SENTENCES("Long-term sentences / Imprisonment for public protection (IPP)", sequence = 5),
  MOTHER_AND_BABY("Mother and baby", sequence = 6, femaleOnly = true),
  OPEN_UNIT("Open unit in a closed establishment", sequence = 7),
  PATHWAY_TO_PROG("Pathway to progression", sequence = 8, secureEstateOnly = true),
  PERINATAL_UNIT("Perinatal unit", sequence = 9, femaleOnly = true),
  PERSONALITY_DISORDER("Personality disorder unit", sequence = 10),
  PIPE("Psychologically informed planned environment (PIPE)", sequence = 11),
  REMAND("Remand", sequence = 12),
  SEPARATION_CENTRE("Separation centre", sequence = 13, secureEstateOnly = true),
  STANDARD_ACCOMMODATION("Standard accommodation", sequence = 14),
  THERAPEUTIC_COMMUNITY("Therapeutic community", sequence = 15),
  VULNERABLE_PRISONERS("Vulnerable prisoners", sequence = 16),
  YOUNG_PERSONS("Young persons", sequence = 17),
  ;

  fun isStandard() = !femaleOnly && !secureEstateOnly
}
