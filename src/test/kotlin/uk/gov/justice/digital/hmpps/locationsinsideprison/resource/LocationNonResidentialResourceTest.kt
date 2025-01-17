package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import org.springframework.test.json.JsonCompareMode
@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationNonResidentialResourceTest : CommonDataTestBase() {

  @DisplayName("POST /locations/non-residential")
  @Nested
  inner class CreateNonResidentialLocationTest {
    var createNonResidentialLocationRequest = CreateNonResidentialLocationRequest(
      prisonId = "MDI",
      code = "ADJ",
      locationType = NonResidentialLocationType.ADJUDICATION_ROOM,
      localName = "Adjudication Room",
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.ADJUDICATION_HEARING, capacity = 15, sequence = 1),
      ),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/non-residential")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest.copy(code = "VISIT", parentId = wingZ.id)))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createNonResidentialLocationRequest.copy(parentId = wingZ.id)))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "code": "ADJ",
              "pathHierarchy": "Z-ADJ",
              "locationType": "${createNonResidentialLocationRequest.locationType}",
              "active": true,
              "key": "MDI-Z-ADJ",
              "localName": "${createNonResidentialLocationRequest.localName}",
              "usage": [
                {
                  "usageType": "ADJUDICATION_HEARING",
                  "capacity": 15,
                  "sequence": 1
                }
              ]
            }
          """, JsonCompareMode.LENIENT,
          )

        getDomainEvents(2).let {
          assertThat(it).hasSize(2)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to "MDI-Z-ADJ",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("PATCH /locations/non-residential/{id}")
  @Nested
  inner class PatchLocationTest {
    val changeCode = PatchResidentialLocationRequest(
      code = "3",
    )

    val changeUsage = PatchNonResidentialLocationRequest(
      code = "MEDICAL",
      locationType = NonResidentialLocationType.APPOINTMENTS,
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.APPOINTMENT, capacity = 20, sequence = 1),
      ),
    )

    val removeUsage = PatchNonResidentialLocationRequest(
      usage = emptySet(),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/non-residential/${landingZ1.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/non-residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/non-residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/non-residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.patch().uri("/locations/non-residential/${landingZ1.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update to existing location`() {
        webTestClient.patch().uri("/locations/non-residential/${adjRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(changeCode.copy(code = "VISIT", parentId = wingZ.id))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "MEDICAL",
              "pathHierarchy": "Z-MEDICAL",
              "locationType": "APPOINTMENTS",
              "active": true,
              "key": "MDI-Z-MEDICAL",
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 20,
                  "sequence": 1
                }
              ]
            }
          """, JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${visitRoom.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
               {
                 "key": "MDI-Z-MEDICAL",
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 20,
                     "sequence": 1
                   }
                 ],
                 "changeHistory": [
                    {
                      "attribute": "Usage",
                      "oldValue": "Visit",
                      "newValue": "Appointment"
                    },
                    {
                      "attribute": "Non residential capacity",
                      "newValue": "20"
                    }
                  ]
               }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can remove details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(removeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": []
            }
          """, JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/key/MDI-Z-VISIT?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": [],
              "changeHistory": [
                {
                  "attribute": "Usage",
                  "oldValue": "Visit"
                }
              ]
            }
          """, JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PATCH /locations/non-residential/key/{key}")
  @Nested
  inner class PatchLocationTestByKey {
    val changeCode = PatchResidentialLocationRequest(
      code = "3",
    )

    val changeUsage = PatchNonResidentialLocationRequest(
      code = "MEDICAL",
      locationType = NonResidentialLocationType.APPOINTMENTS,
      usage = setOf(
        NonResidentialUsageDto(usageType = NonResidentialUsageType.APPOINTMENT, capacity = 20, sequence = 1),
      ),
    )

    val removeUsage = PatchNonResidentialLocationRequest(
      usage = emptySet(),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.patch().uri("/locations/non-residential/key/${landingZ1.getKey()}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.patch().uri("/locations/non-residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.patch().uri("/locations/non-residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.patch().uri("/locations/non-residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeCode))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.patch().uri("/locations/non-residential/key/${landingZ1.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"code": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update to existing location`() {
        webTestClient.patch().uri("/locations/non-residential/key/${adjRoom.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(changeCode.copy(code = "VISIT", parentId = wingZ.id))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/key/${visitRoom.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "MEDICAL",
              "pathHierarchy": "Z-MEDICAL",
              "locationType": "APPOINTMENTS",
              "active": true,
              "key": "MDI-Z-MEDICAL",
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 20,
                  "sequence": 1
                }
              ]
            }
          """, JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${visitRoom.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
               {
                 "key": "MDI-Z-MEDICAL",
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 20,
                     "sequence": 1
                   }
                 ],
                 "changeHistory": [
                    {
                      "attribute": "Usage",
                      "oldValue": "Visit",
                      "newValue": "Appointment"
                    },
                    {
                      "attribute": "Non residential capacity",
                      "newValue": "20"
                    }
                  ]
               }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can remove details of a locations non-res usage`() {
        webTestClient.patch().uri("/locations/non-residential/key/${visitRoom.getKey()}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(removeUsage))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": []
            }
          """, JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/key/MDI-Z-VISIT?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "active": true,
              "key": "MDI-Z-VISIT",
              "usage": [],
              "changeHistory": [
                {
                  "attribute": "Usage",
                  "oldValue": "Visit"
                }
              ]
            }
          """, JsonCompareMode.LENIENT,
          )
      }
    }
  }
}
