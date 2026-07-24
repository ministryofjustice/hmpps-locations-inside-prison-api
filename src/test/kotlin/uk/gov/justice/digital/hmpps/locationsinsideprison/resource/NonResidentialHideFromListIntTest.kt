package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation

/**
 * Hiding a parent non-residential location removes it from the list users maintain, and nothing
 * else. These tests pin down both halves of that: what changes in the listing, and what must
 * stubbornly stay the same everywhere else.
 */
class NonResidentialHideFromListIntTest : CommonDataTestBase() {

  private fun parentWithChild(
    parentLocalName: String = "Parent Location",
    childLocalName: String = "Child Location",
    parentServiceTypes: Set<ServiceType> = emptySet(),
    childServiceTypes: Set<ServiceType> = setOf(ServiceType.APPOINTMENT),
  ): Pair<NonResidentialLocation, NonResidentialLocation> {
    val parent = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT",
        localName = parentLocalName,
        locationType = LocationType.LOCATION,
        serviceTypes = parentServiceTypes,
      ),
    )
    val child = repository.save(
      buildNonResidentialLocation(
        prisonId = "MDI",
        pathHierarchy = "PARENT-CHILD",
        localName = childLocalName,
        locationType = LocationType.LOCATION,
        serviceTypes = childServiceTypes,
      ),
    )
    parent.addChildLocation(child)
    return repository.save(parent) to child
  }

  private fun hide(id: Any) = webTestClient.put().uri("/locations/non-residential/$id/hide")
    .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("read", "write")))
    .exchange()

  @Nested
  inner class Security {
    @Test
    fun `requires authentication`() {
      val (parent, _) = parentWithChild()

      webTestClient.put().uri("/locations/non-residential/${parent.id}/hide")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `requires the MAINTAIN_LOCATIONS role`() {
      val (parent, _) = parentWithChild()

      webTestClient.put().uri("/locations/non-residential/${parent.id}/hide")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS"), scopes = listOf("read")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class Validation {
    @Test
    fun `returns not found for an unknown location`() {
      hide("de91dfa7-821f-4552-a427-bf2f32eafeb0").expectStatus().isNotFound
    }

    @Test
    fun `cannot hide a leaf location, which should be archived instead`() {
      val (_, child) = parentWithChild()

      hide(child.id!!)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(144)
        .jsonPath("$.userMessage").value<String> { assert(it.contains("not a parent location")) }
    }

    @Test
    fun `cannot hide a parent a service still uses`() {
      val (parent, _) = parentWithChild(parentServiceTypes = setOf(ServiceType.APPOINTMENT))

      hide(parent.id!!)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(144)
        .jsonPath("$.userMessage").value<String> { assert(it.contains("still used by")) }
    }

    @Test
    fun `cannot hide a parent that is already hidden`() {
      val (parent, _) = parentWithChild()

      hide(parent.id!!).expectStatus().isOk
      hide(parent.id!!)
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.errorCode").isEqualTo(144)
        .jsonPath("$.userMessage").value<String> { assert(it.contains("already hidden")) }
    }
  }

  @Nested
  inner class Hiding {
    @Test
    fun `hides a parent with no services and reports it as hidden`() {
      val (parent, _) = parentWithChild()

      hide(parent.id!!)
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.hiddenFromList").isEqualTo(true)
        .jsonPath("$.canBeHiddenFromList").isEqualTo(false)
        // Crucially, the location is NOT deactivated
        .jsonPath("$.status").isEqualTo("ACTIVE")
        .jsonPath("$.permanentlyInactive").isEqualTo(false)
    }

    @Test
    fun `flags a parent with no services as hideable, and a leaf or used parent as not`() {
      val (parent, child) = parentWithChild()

      webTestClient.get().uri("/locations/non-residential/${parent.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.canBeHiddenFromList").isEqualTo(true)
        .jsonPath("$.hiddenFromList").isEqualTo(false)

      // A leaf location is archived, never hidden
      webTestClient.get().uri("/locations/non-residential/${child.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.canBeHiddenFromList").isEqualTo(false)
    }

    @Test
    fun `a parent is not hideable while a service that does not show against it still uses it`() {
      // ADJUDICATIONS is not editableInParent, so it never appears in usedByGroupedServices for a
      // parent. The API must still refuse to hide the location, since the service really does use it.
      val (parent, _) = parentWithChild(parentServiceTypes = setOf(ServiceType.HEARING_LOCATION))

      webTestClient.get().uri("/locations/non-residential/${parent.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.usedByGroupedServices").isEmpty
        .jsonPath("$.canBeHiddenFromList").isEqualTo(false)
    }
  }

  @Nested
  inner class ListFiltering {
    private fun summary(query: String) = webTestClient.get().uri("/locations/non-residential/summary/MDI$query")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()

    @Test
    fun `a hidden parent drops out of the active list and appears under archived`() {
      val (parent, _) = parentWithChild()

      summary("?status=ACTIVE").jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").exists()

      hide(parent.id!!).expectStatus().isOk

      summary("?status=ACTIVE").jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").doesNotExist()
      summary("?status=INACTIVE").jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").doesNotExist()
      summary("?status=ARCHIVED").jsonPath("$.locations.content[?(@.localName == 'Parent Location')]").exists()
    }

    @Test
    fun `hiding a parent leaves its children in the list, still active`() {
      val (parent, _) = parentWithChild()

      hide(parent.id!!).expectStatus().isOk

      summary("?status=ACTIVE")
        .jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
        .jsonPath("$.locations.content[?(@.localName == 'Child Location')].status").isEqualTo("ACTIVE")
        .jsonPath("$.locations.content[?(@.localName == 'Child Location')].permanentlyInactive").isEqualTo(false)
    }

    @Test
    fun `a genuinely archived location is still only returned under archived`() {
      val (_, child) = parentWithChild()

      webTestClient.put().uri("/locations/${child.id}/deactivate/permanent")
        .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("read", "write")))
        .bodyValue(mapOf("reason" to "Demolished"))
        .exchange()
        .expectStatus().isOk

      summary("?status=ACTIVE").jsonPath("$.locations.content[?(@.localName == 'Child Location')]").doesNotExist()
      summary("?status=ARCHIVED").jsonPath("$.locations.content[?(@.localName == 'Child Location')]").exists()
    }
  }

  @Nested
  inner class ServicesAreUnaffected {
    @Test
    fun `a child of a hidden parent is still returned to the service that uses it`() {
      val (parent, _) = parentWithChild()

      hide(parent.id!!).expectStatus().isOk

      webTestClient.get().uri("/locations/non-residential/prison/MDI/service/APPOINTMENT")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[?(@.localName == 'Child Location')]").exists()
    }

    @Test
    fun `a child of a hidden parent is still an active location for the prison`() {
      val (parent, _) = parentWithChild()

      hide(parent.id!!).expectStatus().isOk

      webTestClient.get().uri("/locations/prison/MDI/non-residential")
        .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$[?(@.localName == 'Child Location')]").exists()
    }
  }
}
