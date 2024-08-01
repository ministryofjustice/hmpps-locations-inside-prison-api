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

) {
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
) {
  STANDARD_ACCOMMODATION("Standard accommodation"),
  PERSONALITY_DISORDER("Personality disorder unit"),
  THERAPEUTIC_COMMUNITY("Therapeutic community"),
  PIPE("Psychologically informed planned environment (PIPE)"),
  SUB_MISUSE_DRUG_RECOVERY("Drug recovery / Incentivised substance free living (ISFL)"),
  VULNERABLE_PRISONERS("Vulnerable prisoners"),
  FIRST_NIGHT_CENTRE("First night centre / Induction"),
  REMAND("Remand"),
  MOTHER_AND_BABY("Mother and baby"),
  YOUNG_PERSONS("Young persons"),
  HIGH_SECURITY("High security unit"),
  CLOSE_SUPERVISION_CENTRE("Close Supervision Centre (CSC)"),
  PATHWAY_TO_PROG("Pathway to progression"),
  IPP_LONG_TERM_SENTENCES("Long-term sentences / Imprisonment for public protection (IPP)"),
}
