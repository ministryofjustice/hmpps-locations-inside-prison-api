package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
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
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    // Only the child should be returned
    webTestClient.get().uri("/locations/non-residential/summary/MDI?filterParents=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").doesNotExist()
      .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
  }

  @Test
  fun `property-only locations are excluded unless includeProperty is set`() {
    // Solely used for property storage (its only usage is PROPERTY).
    repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PROPBOX",
        localName = "Property Box",
        locationType = LocationType.BOX,
        usageTypes = setOf(NonResidentialUsageType.PROPERTY),
      ),
    )

    // Property storage is excluded by default...
    webTestClient.get().uri("/locations/non-residential/summary/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Property Box')]").doesNotExist()

    // ...but returned when includeProperty=true.
    webTestClient.get().uri("/locations/non-residential/summary/MDI?includeProperty=true")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Property Box')]").exists()
  }

  @Test
  fun `locations used for property and another service are always returned`() {
    // Used for property AND appointments - should not be filtered out.
    repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "MIXED",
        localName = "Mixed Use",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
        usageTypes = setOf(NonResidentialUsageType.PROPERTY),
      ),
    )

    webTestClient.get().uri("/locations/non-residential/summary/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Mixed Use')]").exists()
  }

  @Test
  fun `box locations without a property usage are still returned`() {
    // A BOX with no PROPERTY usage is not property storage, so location type alone must not exclude it.
    repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PLAINBOX",
        localName = "Plain Box",
        locationType = LocationType.BOX,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )

    webTestClient.get().uri("/locations/non-residential/summary/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Plain Box')]").exists()
  }

  @Test
  fun `can retrieve all non-residential locations`() {
    val parentNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = "Parent Location",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.APPOINTMENT),
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    webTestClient.get().uri("/locations/non-residential/summary/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").exists()
      .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
  }

  @Test
  fun `parents filter service types that cannot be changed`() {
    val parentNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "PVI",
        pathHierarchy = "PARENT",
        localName = "Parent Location",
        serviceTypes = setOf(ServiceType.APPOINTMENT, ServiceType.USE_OF_FORCE),
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "PVI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        serviceTypes = setOf(ServiceType.APPOINTMENT, ServiceType.USE_OF_FORCE),
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    webTestClient.get().uri("/locations/non-residential/summary/PVI")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody().json(
        """
          {
            "prisonId": "PVI",
            "locations": {
              "content": [
                {
                  "localName": "Child Location",
                  "code": "CHILD",
                  "pathHierarchy": "PARENT-CHILD",
                  "isLeafLevel": true,
                  "usedByGroupedServices": [
                    "ACTIVITIES_APPOINTMENTS",
                    "USE_OF_FORCE"
                  ],
                  "usedByServices": [
                    "APPOINTMENT",
                    "USE_OF_FORCE"
                  ],
                  "level": 2,
                  "key": "PVI-PARENT-CHILD"
                },
                {
                  "localName": "Parent Location",
                  "code": "PARENT",
                  "pathHierarchy": "PARENT",
                  "isLeafLevel": false,
                  "usedByGroupedServices": [
                    "ACTIVITIES_APPOINTMENTS"
                  ],
                  "usedByServices": [
                    "APPOINTMENT"
                  ],
                  "level": 1,
                  "key": "PVI-PARENT"
                }
              ],
              "numberOfElements": 2,
              "totalElements": 2,
              "totalPages": 1
            }
          }
         """,
        JsonCompareMode.LENIENT,
      )
  }

  @Test
  fun `parent location not returned when filtering by a service type that does not show parents`() {
    val parentNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = "Parent Location",
        locationType = LocationType.AREA,
        serviceTypes = setOf(ServiceType.USE_OF_FORCE),
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.USE_OF_FORCE),
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    // Only the child should be returned
    webTestClient.get().uri("/locations/non-residential/summary/MDI?serviceFamilyType=USE_OF_FORCE")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").doesNotExist()
      .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
  }

  @Test
  fun `parent location is returned when filtering by a service type that does show parents`() {
    val parentNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = "Parent Location",
        locationType = LocationType.AREA,
        serviceTypes = setOf(ServiceType.VIDEO_LINK),
      ),
    )
    val childNonRes = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = "Child Location",
        locationType = LocationType.LOCATION,
        serviceTypes = setOf(ServiceType.VIDEO_LINK),
      ),
    )
    parentNonRes.addChildLocation(childNonRes)
    repository.save(parentNonRes)

    // Only the child should be returned
    webTestClient.get().uri("/locations/non-residential/summary/MDI?serviceFamilyType=VIDEO_LINK_APPOINTMENTS")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").exists()
      .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
  }
}
