package uk.gov.justice.digital.hmpps.locationsinsideprison.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.LocationService
import java.time.Clock
import java.time.LocalDateTime

@Schema(description = "Request to create a wing")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateWingRequest(
  @Schema(description = "Prison ID", example = "MDI", required = true)
  val prisonId: String,
  @Schema(description = "Code assigned to a wing", example = "B", required = true)
  @field:Size(max = 12, message = "Max of 12 characters")
  val wingCode: String,
  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  @field:Size(max = 80, message = "Max of 80 characters")
  val wingDescription: String?,
  @Schema(description = "Number of landings required", example = "3", required = false)
  @field:Max(value = 10, message = "Max of 10")
  val numberOfLandings: Int? = null,
  @Schema(description = "Number of spurs required", example = "2", required = false)
  @field:Max(value = 10, message = "Max of 10")
  val numberOfSpursPerLanding: Int? = null,
  @Schema(description = "Number of cells required in each section (wing,landing or spur)", example = "40", required = true)
  @field:Max(value = 999, message = "Max of 999")
  val numberOfCellsPerSection: Int,
  @Schema(description = "Default Cell Capacity", example = "1", required = true, defaultValue = "1")
  @field:Max(value = 10, message = "Max of 10")
  val defaultCellCapacity: Int = 1,
  @Schema(description = "Default set of attributes", example = "SINGLE_OCCUPANCY", required = false)
  val defaultAttributesOfCells: Set<ResidentialAttributeValue>?,
) {

  fun toEntity(createdBy: String, clock: Clock): ResidentialLocation {
    val wing = ResidentialLocation(
      prisonId = prisonId,
      code = wingCode,
      locationType = LocationType.WING,
      pathHierarchy = wingCode,
      description = wingDescription,
      orderWithinParentLocation = 1,
      createdBy = createdBy,
      whenCreated = LocalDateTime.now(clock),
      childLocations = mutableListOf(),
    )
    LocationService.log.info("Created Wing [${wing.getKey()}]")

    numberOfLandings?.let { numberOfLandings ->
      for (landingNumber in 1..numberOfLandings) {
        val landing = ResidentialLocation(
          prisonId = prisonId,
          code = "$landingNumber",
          locationType = LocationType.LANDING,
          pathHierarchy = "$wingCode-$landingNumber",
          description = "Landing $landingNumber on Wing $wingCode",
          orderWithinParentLocation = landingNumber,
          createdBy = createdBy,
          whenCreated = LocalDateTime.now(clock),
          childLocations = mutableListOf(),
        )
        wing.addChildLocation(landing)
        LocationService.log.info("Created Landing [${landing.getKey()}]")
      }
    }

    wing.findAllLeafLocations().forEach { landing ->
      numberOfSpursPerLanding?.let { numberOfSpurs ->
        for (spurNumber in 1..numberOfSpurs) {
          val spur = ResidentialLocation(
            prisonId = prisonId,
            code = "$spurNumber",
            locationType = LocationType.SPUR,
            pathHierarchy = "${landing.getPathHierarchy()}-$spurNumber",
            description = "Spur $spurNumber on Landing ${landing.getCode()}",
            orderWithinParentLocation = spurNumber,
            createdBy = createdBy,
            whenCreated = LocalDateTime.now(clock),
            childLocations = mutableListOf(),
          )
          landing.addChildLocation(spur)
          LocationService.log.info("Created Spur [${spur.getKey()}]")
        }
      }
    }

    wing.findAllLeafLocations().forEach { leaf ->
      for (cellNumber in 1..numberOfCellsPerSection) {
        val code = "%03d".format(cellNumber)
        val cell = Cell(
          prisonId = prisonId,
          code = code,
          pathHierarchy = "${leaf.getPathHierarchy()}-$code",
          description = "Cell $cellNumber on ${leaf.getCode()}",
          orderWithinParentLocation = cellNumber,
          createdBy = createdBy,
          whenCreated = LocalDateTime.now(clock),
          childLocations = mutableListOf(),
          capacity = Capacity(
            capacity = defaultCellCapacity,
            operationalCapacity = defaultCellCapacity,
          ),
          certification = Certification(
            certified = true,
            capacityOfCertifiedCell = defaultCellCapacity,
          ),
        )
        defaultAttributesOfCells?.map { cell.addAttribute(it) }
        leaf.addChildLocation(cell)
        LocationService.log.info("Created Cell [${cell.getKey()}]")
      }
    }
    return wing
  }
}
