package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LinkedTransaction
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import java.time.Clock
import java.time.LocalDateTime

@Schema(description = "Request to create a wing")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateEntireWingRequest(
  @param:Schema(description = "Prison ID where the location is situated", required = true, example = "MDI", minLength = 3, maxLength = 5, pattern = "^[A-Z]{2}I|ZZGHI$")
  @field:Size(min = 3, message = "Prison ID cannot be blank")
  @field:Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
  @field:Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
  val prisonId: String,
  @param:Schema(description = "Code assigned to a wing", example = "B", required = true)
  @field:Size(max = 12, message = "Max of 12 characters")
  val wingCode: String,
  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Max of 80 characters")
  val wingDescription: String?,

  @param:Schema(description = "Number of spurs required", example = "2", required = false)
  @field:Max(value = 4, message = "Max of 4")
  val numberOfSpurs: Int? = null,

  @param:Schema(description = "Number of landings required", example = "3", required = false)
  @field:Max(value = 5, message = "Max of 5")
  val numberOfLandings: Int? = null,

  @param:Schema(description = "Number of cells required in each section (wing,landing or spur)", example = "40", required = true)
  @field:Max(value = 100, message = "Max of 100")
  val numberOfCellsPerSection: Int,

  @param:Schema(description = "Default working capacity", example = "1", required = false, defaultValue = "1")
  @field:Max(value = 4, message = "Max of 4")
  val defaultWorkingCapacity: Int = 1,

  @param:Schema(description = "Default max capacity", example = "1", required = false, defaultValue = "1")
  @field:Max(value = 4, message = "Max of 4")
  val defaultMaxCapacity: Int = 1,

  @param:Schema(description = "Default CNA", example = "1", required = false, defaultValue = "1")
  @field:Max(value = 4, message = "Max of 4")
  val defaultCNA: Int = 1,

  @param:Schema(description = "The structure of the wing", required = false)
  val wingStructure: List<ResidentialStructuralType>? = null,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun toEntity(createdBy: String, clock: Clock, linkedTransaction: LinkedTransaction, createInDraft: Boolean = false): ResidentialLocation {
    val status = if (createInDraft) LocationStatus.DRAFT else LocationStatus.ACTIVE
    val wing = ResidentialLocation(
      prisonId = prisonId,
      code = wingCode,
      locationType = LocationType.WING,
      status = status,
      pathHierarchy = wingCode,
      localName = wingDescription,
      orderWithinParentLocation = 1,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = mutableListOf(),
    ).apply {
      wingStructure?.let { setStructure(it) }
      addHistory(
        attributeName = LocationAttribute.LOCATION_CREATED,
        oldValue = null,
        newValue = getKey(),
        amendedBy = createdBy,
        amendedDate = LocalDateTime.now(clock),
        linkedTransaction = linkedTransaction,
      )
      log.info("Created Wing [${this.getKey()}]")
    }

    numberOfSpurs?.let { numberOfSpurs ->
      for (spurNumber in 1..numberOfSpurs) {
        ResidentialLocation(
          prisonId = prisonId,
          code = "$spurNumber",
          locationType = LocationType.SPUR,
          status = status,
          pathHierarchy = "$wingCode-$spurNumber",
          localName = "Spur $spurNumber on Wing $wingCode",
          orderWithinParentLocation = spurNumber,
          createdBy = createdBy,
          whenCreated = LocalDateTime.now(clock),
          childLocations = mutableListOf(),
        ).apply {
          wing.addChildLocation(this)
          addHistory(
            attributeName = LocationAttribute.LOCATION_CREATED,
            oldValue = null,
            newValue = getKey(),
            amendedBy = createdBy,
            amendedDate = LocalDateTime.now(clock),
            linkedTransaction = linkedTransaction,
          )
          log.info("Created Spur [${this.getKey()}]")
        }
      }
    }

    wing.findAllLeafLocations().forEach { spur ->
      numberOfLandings?.let { numberOfLandings ->
        for (landingNumber in 1..numberOfLandings) {
          ResidentialLocation(
            prisonId = prisonId,
            code = "$landingNumber",
            locationType = LocationType.LANDING,
            status = status,
            pathHierarchy = "${spur.getPathHierarchy()}-$landingNumber",
            localName = "Landing $landingNumber on ${if ((numberOfSpurs ?: 0) > 0) "Spur " + spur.getLocationCode() else "Wing " + wing.getLocationCode()}",
            orderWithinParentLocation = landingNumber,
            createdBy = createdBy,
            whenCreated = LocalDateTime.now(clock),
            childLocations = mutableListOf(),
          ).apply {
            spur.addChildLocation(this)

            addHistory(
              attributeName = LocationAttribute.LOCATION_CREATED,
              oldValue = null,
              newValue = getKey(),
              amendedBy = createdBy,
              amendedDate = LocalDateTime.now(clock),
              linkedTransaction = linkedTransaction,
            )
            log.info("Created Landing [${this.getKey()}]")
          }
        }
      }
    }

    wing.findAllLeafLocations().forEach { leaf ->
      for (cellNumber in 1..numberOfCellsPerSection) {
        val code = "%03d".format(cellNumber)
        Cell(
          prisonId = prisonId,
          code = code,
          cellMark = "$wingCode-$cellNumber",
          inCellSanitation = true,
          pathHierarchy = "${leaf.getPathHierarchy()}-$code",
          status = status,
          localName = "Cell $cellNumber on ${leaf.getLocationCode()}",
          accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
          orderWithinParentLocation = cellNumber,
          createdBy = createdBy,
          whenCreated = LocalDateTime.now(clock),
          childLocations = mutableListOf(),
          capacity = Capacity(
            maxCapacity = defaultMaxCapacity,
            workingCapacity = defaultWorkingCapacity,
          ),
          certification = Certification(
            certified = status != LocationStatus.DRAFT,
            certifiedNormalAccommodation = defaultCNA,
          ),
        ).apply {
          addUsedFor(UsedForType.STANDARD_ACCOMMODATION, createdBy, clock, linkedTransaction = linkedTransaction)
          addSpecialistCellType(SpecialistCellType.ESCAPE_LIST, linkedTransaction = linkedTransaction, userOrSystemInContext = createdBy, clock = clock)
          leaf.addChildLocation(this)

          addHistory(
            attributeName = LocationAttribute.LOCATION_CREATED,
            oldValue = null,
            newValue = getKey(),
            amendedBy = createdBy,
            amendedDate = LocalDateTime.now(clock),
            linkedTransaction = linkedTransaction,
          )
          log.info("Created Cell [${this.getKey()}]")
        }
      }
    }
    return wing
  }
}
