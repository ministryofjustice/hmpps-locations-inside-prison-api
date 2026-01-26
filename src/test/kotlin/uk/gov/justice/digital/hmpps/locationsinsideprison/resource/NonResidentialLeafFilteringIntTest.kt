package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation

class NonResidentialLeafFilteringIntTest : CommonDataTestBase() {

  @Test
  fun `can retrieve only leaf non-residential locations`() {
    val parentNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = "Parent Location",
        locationType = LocationType.LOCATION,
        serviceType = ServiceType.APPOINTMENT,
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        locationType = LocationType.LOCATION,
        serviceType = ServiceType.APPOINTMENT,
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    // Only the child should be returned
    // This is expected to fail (Parent Location will still exist) until we implement the fix
    webTestClient.get().uri("/locations/non-residential/summary/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").doesNotExist()
      .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
  }
}
