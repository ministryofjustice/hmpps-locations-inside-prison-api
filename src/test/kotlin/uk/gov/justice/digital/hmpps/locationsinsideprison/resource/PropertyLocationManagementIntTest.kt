package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PropertyLocationDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType

class PropertyLocationManagementIntTest : CommonDataTestBase() {

  private fun manageHeaders() = setAuthorisation(roles = listOf("ROLE_MANAGE_PROPERTY_LOCATIONS"), scopes = listOf("write"))

  private fun createPropertyLocation(localName: String, capacity: Int): PropertyLocationDto = webTestClient.post()
    .uri("/locations/prison/MDI/property")
    .headers(manageHeaders())
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue("""{ "localName": "$localName", "capacity": $capacity }""")
    .exchange()
    .expectStatus().isCreated
    .expectBody(PropertyLocationDto::class.java)
    .returnResult().responseBody!!

  @Test
  fun `create requires the MANAGE_PROPERTY_LOCATIONS role`() {
    webTestClient.post().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{ "localName": "Reception store", "capacity": 10 }""")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `creates a top-level BOX property location with a generated code and capacity`() {
    val created = createPropertyLocation("Reception store", 10)

    assertThat(created.prisonId).isEqualTo("MDI")
    // localName is title-cased for display by the DTO formatter.
    assertThat(created.localName).isEqualTo("Reception Store")
    assertThat(created.locationType).isEqualTo(LocationType.BOX)
    assertThat(created.capacity).isEqualTo(10)
    assertThat(created.code).isNotBlank()

    // The new location shows up in the property list with its capacity.
    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[?(@.localName == 'Reception Store')].locationType").isEqualTo("BOX")
      .jsonPath("$[?(@.localName == 'Reception Store')].capacity").isEqualTo(10)
  }

  @Test
  fun `updates a property location's name and capacity`() {
    val created = createPropertyLocation("Reception store", 10)

    webTestClient.put().uri("/locations/property/${created.id}")
      .headers(manageHeaders())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{ "localName": "Reception store large", "capacity": 25 }""")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.localName").isEqualTo("Reception Store Large")
      .jsonPath("$.capacity").isEqualTo(25)

    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[?(@.localName == 'Reception Store Large')].capacity").isEqualTo(25)
      .jsonPath("$[?(@.localName == 'Reception Store')]").doesNotExist()
  }

  @Test
  fun `removing the property designation drops it from the property list`() {
    val created = createPropertyLocation("Temporary store", 5)

    webTestClient.delete().uri("/locations/property/${created.id}")
      .headers(manageHeaders())
      .exchange()
      .expectStatus().isOk

    webTestClient.get().uri("/locations/prison/MDI/property")
      .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$[?(@.localName == 'Temporary store')]").doesNotExist()
  }

  @Test
  fun `rejects a duplicate local name in the same prison`() {
    createPropertyLocation("Duplicate store", 10)

    webTestClient.post().uri("/locations/prison/MDI/property")
      .headers(manageHeaders())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{ "localName": "Duplicate store", "capacity": 10 }""")
      .exchange()
      .expectStatus().isEqualTo(409)
  }

  @Test
  fun `rejects a negative capacity`() {
    webTestClient.post().uri("/locations/prison/MDI/property")
      .headers(manageHeaders())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{ "localName": "Bad store", "capacity": -1 }""")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `update returns 404 for an unknown location`() {
    webTestClient.put().uri("/locations/property/${java.util.UUID.randomUUID()}")
      .headers(manageHeaders())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{ "capacity": 5 }""")
      .exchange()
      .expectStatus().isNotFound
  }
}
