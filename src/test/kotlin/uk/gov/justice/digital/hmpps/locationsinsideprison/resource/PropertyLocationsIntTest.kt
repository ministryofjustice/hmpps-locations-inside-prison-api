package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation

class PropertyLocationsIntTest : CommonDataTestBase() {

  @Test
  fun `requires the VIEW_LOCATIONS role`() {
    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `returns property-usage locations of any location type, with their capacity`() {
    // A non-BOX property location, and a BOX property location with an explicit capacity.
    val store = buildNonResidentialLocation(
      prisonId = "MDI",
      pathHierarchy = "PSTORE",
      localName = "Property Store",
      locationType = LocationType.STORE,
      usageTypes = setOf(NonResidentialUsageType.PROPERTY),
    )
    store.addUsage(NonResidentialUsageType.PROPERTY, 700)
    repository.save(store)

    val box = buildNonResidentialLocation(
      prisonId = "MDI",
      pathHierarchy = "PBOX",
      localName = "Property Box",
      locationType = LocationType.BOX,
      usageTypes = setOf(NonResidentialUsageType.PROPERTY),
    )
    box.addUsage(NonResidentialUsageType.PROPERTY, 1)
    repository.save(box)

    // A non-residential location WITHOUT a property usage - must be excluded.
    repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "APPT",
        localName = "Appointment Room",
        locationType = LocationType.ROOM,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )

    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[?(@.localName == 'Property Store')].locationType").isEqualTo("STORE")
      .jsonPath("$[?(@.localName == 'Property Store')].capacity").isEqualTo(700)
      .jsonPath("$[?(@.localName == 'Property Box')].locationType").isEqualTo("BOX")
      .jsonPath("$[?(@.localName == 'Property Box')].capacity").isEqualTo(1)
      .jsonPath("$[?(@.localName == 'Appointment Room')]").doesNotExist()
  }

  @Test
  fun `get by id requires the VIEW_LOCATIONS role`() {
    webTestClient.get().uri("/locations/property/${java.util.UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `get by id returns the property location with its capacity`() {
    val box = buildNonResidentialLocation(
      prisonId = "MDI",
      pathHierarchy = "PBOX",
      localName = "Property Box",
      locationType = LocationType.BOX,
      usageTypes = setOf(NonResidentialUsageType.PROPERTY),
    )
    box.addUsage(NonResidentialUsageType.PROPERTY, 12)
    val saved = repository.save(box)

    webTestClient.get().uri("/locations/property/${saved.id}")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.id").isEqualTo(saved.id.toString())
      .jsonPath("$.localName").isEqualTo("Property Box")
      .jsonPath("$.locationType").isEqualTo("BOX")
      .jsonPath("$.capacity").isEqualTo(12)
  }

  @Test
  fun `get by id returns 404 for a non-residential location that cannot store property`() {
    val appt = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "APPT",
        localName = "Appointment Room",
        locationType = LocationType.ROOM,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )

    webTestClient.get().uri("/locations/property/${appt.id}")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `get by id returns 404 for an unknown id`() {
    webTestClient.get().uri("/locations/property/${java.util.UUID.randomUUID()}")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `returns leaf property locations only - a property parent whose child is also property is dropped`() {
    val parent = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = "Parent Property",
        locationType = LocationType.AREA,
        usageTypes = setOf(NonResidentialUsageType.PROPERTY),
      ),
    )
    val child = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Property",
        locationType = LocationType.BOX,
        usageTypes = setOf(NonResidentialUsageType.PROPERTY),
      ),
    )
    parent.addChildLocation(child)
    repository.save(parent)

    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[?(@.localName == 'Parent Property')]").doesNotExist()
      .jsonPath("$[?(@.localName == 'Child Property')]").exists()
  }
}
