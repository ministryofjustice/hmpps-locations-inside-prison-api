package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

/**
 * Whether a location is a cell depends on how it is stored, not just its type. A PATCH that changes the type to or
 * from a cell must change how it is stored too, or capacity changes are silently ignored (MAPB-999).
 */
@WithMockAuthUser(username = EXPECTED_USERNAME)
@DisplayName("PATCH /locations/residential/{id} changing the location type to or from a cell")
class ChangeLocationTypeResourceTest : CommonDataTestBase() {

  lateinit var area: ResidentialLocation

  @BeforeEach
  fun createArea() {
    area = TransactionTemplate(transactionManager).execute {
      val landing = resiRepository.findById(landingZ1.id!!).get()
      repository.save(
        buildResidentialLocation(pathHierarchy = "Z-1-AREA", locationType = LocationType.AREA).apply { setParent(landing) },
      ) as ResidentialLocation
    }!!
  }

  private fun patchLocationType(id: Any, locationType: ResidentialLocationType) {
    webTestClient.patch().uri("/locations/residential/$id")
      .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
      .header("Content-Type", "application/json")
      .bodyValue(jsonString(PatchResidentialLocationRequest(locationType = locationType)))
      .exchange()
      .expectStatus().isOk
  }

  private fun putCapacity(id: Any) = webTestClient.put().uri("/locations/$id/capacity")
    .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
    .header("Content-Type", "application/json")
    .bodyValue("""{ "maxCapacity": 2, "workingCapacity": 2, "certifiedNormalAccommodation": 2 }""")
    .exchange()

  @Test
  fun `an area changed to a cell is stored as a cell and keeps the capacity it is then given`() {
    patchLocationType(area.id!!, ResidentialLocationType.CELL)

    assertThat(cellRepository.findById(area.id!!)).isPresent

    prisonerSearchMockServer.stubSearchByLocations(area.prisonId, listOf(area.getPathHierarchy()), false)
    putCapacity(area.id!!).expectStatus().isOk

    val stored = storedCellState(area.id!!)
    assertThat(stored.maxCapacity).isEqualTo(2)
    assertThat(stored.workingCapacity).isEqualTo(2)
    assertThat(stored.certifiedNormalAccommodation).isEqualTo(2)

    val typeChanges = TransactionTemplate(transactionManager).execute {
      resiRepository.findById(area.id!!).get().getHistoryAsList()
        .filter { it.attributeName == LocationAttribute.LOCATION_TYPE }
        .map { it.oldValue to it.newValue }
    }
    assertThat(typeChanges).containsExactly("Area" to "Cell")
  }

  @Test
  fun `a cell changed to another type is no longer stored as a cell`() {
    patchLocationType(cell1.id!!, ResidentialLocationType.AREA)

    assertThat(cellRepository.findById(cell1.id!!)).isEmpty
    assertThat(resiRepository.findById(cell1.id!!).get().locationType).isEqualTo(LocationType.AREA)
  }

  @Test
  fun `setting the capacity of a location that is not a cell is rejected`() {
    putCapacity(area.id!!)
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.developerMessage").isEqualTo("Location MDI-Z-1-AREA is not a cell, so its capacity cannot be set")
  }
}
