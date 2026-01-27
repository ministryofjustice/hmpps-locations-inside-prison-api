package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.expectBodyList
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateOrUpdateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.generateNonResidentialCode
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationNonResidentialResourceTest : CommonDataTestBase() {

  @DisplayName("POST /locations/non-residential/{prisonId}")
  @Nested
  inner class CreateSimpleNonResidentialLocationTest {

    lateinit var classroom1: NonResidentialLocation

    @BeforeEach
    fun setUp() {
      classroom1 = repository.save(
        buildNonResidentialLocation(
          localName = "Classroom One",
          serviceType = ServiceType.PROGRAMMES_AND_ACTIVITIES,
        ),
      )
    }
    var createReq = CreateOrUpdateNonResidentialLocationRequest(
      localName = "Adjudication Room",
      servicesUsingLocation = setOf(ServiceType.HEARING_LOCATION, ServiceType.INTERNAL_MOVEMENTS),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createReq))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createReq))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createReq))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.post().uri("/locations/non-residential/MDIl")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"localName": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `duplicate location is rejected`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createReq.copy(localName = classroom1.localName!!)))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can create details of a location`() {
        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(createReq.copy(localName = "Visit Room 2")))
          .exchange()
          .expectStatus().isCreated
          .expectBody().json(
            // language=json
            """ 
             {
              "prisonId": "MDI",
              "key": "MDI-VSTRM50",
              "localName": "Visit Room 2",
              "code": "VSTRM50",
              "pathHierarchy": "VSTRM50",
              "locationType": "LOCATION",
              "permanentlyInactive": false,
              "usedByGroupedServices": [
                "ADJUDICATIONS",
                "INTERNAL_MOVEMENTS"
              ],
              "usedByServices": [
                "HEARING_LOCATION",
                "INTERNAL_MOVEMENTS"
              ],
              "status": "ACTIVE",
              "level": 1
            }
          """,
            JsonCompareMode.LENIENT,
          )

        val newKey = "MDI-${generateNonResidentialCode("MDI", "Visit Room 2")}"

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.created" to newKey,
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/non-residential/{id}")
  @Nested
  inner class UpdateNonResidentialLocationTest {

    lateinit var classroom2: NonResidentialLocation
    lateinit var inactiveClassroom3: NonResidentialLocation

    @BeforeEach
    fun setUp() {
      classroom2 = repository.save(
        buildNonResidentialLocation(
          localName = "Classroom Two",
          serviceType = ServiceType.PROGRAMMES_AND_ACTIVITIES,
        ),
      )
      inactiveClassroom3 = repository.save(
        buildNonResidentialLocation(
          localName = "Inactive Classroom Three",
          serviceType = ServiceType.PROGRAMMES_AND_ACTIVITIES,
          status = LocationStatus.INACTIVE,
        ),
      )
    }

    var updateReq = CreateOrUpdateNonResidentialLocationRequest(
      localName = "Visit Room",
      servicesUsingLocation = setOf(ServiceType.OFFICIAL_VISITS),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"localName": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `Duplicate local name is rejected`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(updateReq.copy(localName = "Adjudication Room", servicesUsingLocation = setOf(ServiceType.HEARING_LOCATION)))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can deactivate a location`() {
        // update classroom 2 to classroom 3
        webTestClient.put().uri("/locations/non-residential/${classroom2.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(updateReq.copy(active = false, localName = "Classroom Two made inactive"))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
          {
            "key": "${classroom2.getKey()}",
            "status": "INACTIVE"
          }
        """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(2).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to classroom2.getKey(),
            "location.inside.prison.amended" to classroom2.getKey(),
          )
        }
      }

      @Test
      fun `can reactivate a location`() {
        // update classroom 2 to classroom 3
        webTestClient.put().uri("/locations/non-residential/${inactiveClassroom3.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(updateReq.copy(active = true, localName = "Classroom Three made active"))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
          {
            "key": "${inactiveClassroom3.getKey()}",
            "status": "ACTIVE"
          }
        """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(2).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.reactivated" to inactiveClassroom3.getKey(),
            "location.inside.prison.amended" to inactiveClassroom3.getKey(),
          )
        }
      }

      @Test
      fun `local name can be reused`() {
        // update classroom 2 to classroom 3
        webTestClient.put().uri("/locations/non-residential/${classroom2.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(updateReq.copy(localName = "Classroom Three"))
          .exchange()
          .expectStatus().isOk

        webTestClient.post().uri("/locations/non-residential/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq.copy(localName = classroom2.localName!!)))
          .exchange()
          .expectStatus().isCreated

        val newKey = "MDI-${generateNonResidentialCode("MDI", classroom2.localName!!, checksumDigits = 3, maxSize = 9)}"
        getDomainEvents(2).let {
          assertThat(it).hasSize(2)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to classroom2.getKey(),
            "location.inside.prison.created" to newKey,
          )
        }
      }

      @Test
      fun `can update details of the local name of a non-residential location`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              updateReq.copy(localName = "Visit Room 2"),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
        {
          "prisonId": "MDI",
          "localName": "Visit Room 2",
          "code": "VISIT",
          "pathHierarchy": "Z-VISIT",
          "locationType": "VISITS",
          "permanentlyInactive": false,
          "usedByGroupedServices": [
            "OFFICIAL_VISITS"
          ],
          "usedByServices": [
            "OFFICIAL_VISITS"
          ],
          "status": "ACTIVE",
          "level": 2,
          "key": "MDI-Z-VISIT"
        }
        """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to visitRoom.getKey(),
          )
        }

        webTestClient.get().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "prisonId": "MDI",
              "localName": "Visit Room 2",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "permanentlyInactive": false,
              "usedByGroupedServices": [
                "OFFICIAL_VISITS"
              ],
              "usedByServices": [
                "OFFICIAL_VISITS"
              ],
              "status": "ACTIVE",
              "level": 2,
              "key": "MDI-Z-VISIT"
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can remove details of a locations of used by service`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq.copy(servicesUsingLocation = emptySet())))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {
              "prisonId": "MDI",
              "localName": "Visit Room",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "permanentlyInactive": false,
              "usedByGroupedServices": [],
              "usedByServices": [],
              "status": "ACTIVE",
              "level": 2,
              "key": "MDI-Z-VISIT"
            }
        """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to visitRoom.getKey(),
          )
        }

        webTestClient.get().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
            {
              "prisonId": "MDI",
              "localName": "Visit Room",
              "code": "VISIT",
              "pathHierarchy": "Z-VISIT",
              "locationType": "VISITS",
              "permanentlyInactive": false,
              "usedByGroupedServices": [],
              "usedByServices": [],
              "status": "ACTIVE",
              "level": 2,
              "key": "MDI-Z-VISIT"
            } 
        """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can set internal movement using service type`() {
        webTestClient.put().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(updateReq.copy(servicesUsingLocation = setOf(ServiceType.INTERNAL_MOVEMENTS))))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
          {
            "prisonId": "MDI",
            "localName": "Visit Room",
            "code": "VISIT",
            "pathHierarchy": "Z-VISIT",
            "locationType": "VISITS",
            "permanentlyInactive": false,
            "usedByGroupedServices": [
              "INTERNAL_MOVEMENTS"
            ],
            "usedByServices": [
              "INTERNAL_MOVEMENTS"
            ],
            "status": "ACTIVE",
            "level": 2,
            "key": "MDI-Z-VISIT"
          }
        """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it).hasSize(1)
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to visitRoom.getKey(),
          )
        }

        webTestClient.get().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
               {
                "prisonId": "MDI",
                "localName": "Visit Room",
                "code": "VISIT",
                "pathHierarchy": "Z-VISIT",
                "locationType": "VISITS",
                "permanentlyInactive": false,
                "usedByGroupedServices": [
                  "INTERNAL_MOVEMENTS"
                ],
                "usedByServices": [
                  "INTERNAL_MOVEMENTS"
                ],
                "status": "ACTIVE",
                "level": 2,
                "key": "MDI-Z-VISIT"
              }
        """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("POST /locations/non-residential")
  @Nested
  inner class CreateNonResidentialLocationTest {
    var createNonResidentialLocationRequest = CreateNonResidentialLocationRequest(
      prisonId = "MDI",
      code = "ADJ",
      locationType = NonResidentialLocationType.ADJUDICATION_ROOM,
      localName = "Adjudication Room",
      servicesUsingLocation = setOf(ServiceType.HEARING_LOCATION, ServiceType.INTERNAL_MOVEMENTS),
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
              "internalMovementAllowed": true,
              "localName": "${createNonResidentialLocationRequest.localName}",
              "usage": [
                {
                  "usageType": "ADJUDICATION_HEARING",
                  "capacity": 99,
                  "sequence": 99
                },
                {
                  "usageType": "MOVEMENT",
                  "capacity": 99,
                  "sequence": 99
                }
              ],
              "servicesUsingLocation": [
                {
                  "serviceType": "HEARING_LOCATION",
                  "serviceFamilyType": "ADJUDICATIONS"
                },
                {
                  "serviceType": "INTERNAL_MOVEMENTS",
                  "serviceFamilyType": "INTERNAL_MOVEMENTS"
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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

    val changeService = PatchNonResidentialLocationRequest(
      code = "MEDICAL",
      locationType = NonResidentialLocationType.APPOINTMENTS,
      servicesUsingLocation = setOf(ServiceType.APPOINTMENT),
    )

    val removeService = PatchNonResidentialLocationRequest(
      servicesUsingLocation = emptySet(),
    )

    val addInternalMovement = PatchNonResidentialLocationRequest(
      servicesUsingLocation = setOf(ServiceType.INTERNAL_MOVEMENTS),
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
          .bodyValue(
            jsonString(
              PatchNonResidentialLocationRequest(
                servicesUsingLocation = setOf(ServiceType.PROGRAMMES_AND_ACTIVITIES),
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk

        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(changeService))
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
              "servicesUsingLocation": [
              {
                "serviceType": "APPOINTMENT",
                "serviceFamilyType": "ACTIVITIES_APPOINTMENTS"
                }
              ],
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 99,
                  "sequence": 99
                },
                {
                  "usageType": "PROPERTY",
                  "capacity": 10,
                  "sequence": 99
                },
                {
                  "usageType": "OTHER",
                  "capacity": 10,
                  "sequence": 99
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${visitRoom.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
               {
                 "key": "MDI-Z-MEDICAL",
                 "servicesUsingLocation": [
                  {
                    "serviceType": "APPOINTMENT",
                    "serviceFamilyType": "ACTIVITIES_APPOINTMENTS"
                    }
                  ],
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 99,
                     "sequence": 99
                   },
                   {
                    "usageType": "PROPERTY",
                    "capacity": 10,
                    "sequence": 99
                  },
                  {
                    "usageType": "OTHER",
                    "capacity": 10,
                    "sequence": 99
                  }
                 ],
                 "changeHistory": [
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Use by service",
                     "oldValues": [
                       "Programmes and activities"
                     ],
                     "newValues": [
                       "Appointments"
                     ]
                   },
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Usage",
                     "oldValues": [
                       "Programmes/activities"
                     ],
                     "newValues": [
                       "Appointment"
                     ]
                   },
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Non residential capacity",
                     "newValues": [
                       "99"
                     ]
                   },
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Use by service",
                     "oldValues": [
                       "Official visits"
                     ],
                     "newValues": [
                       "Programmes and activities"
                     ]
                    },
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Usage",
                     "oldValues": [
                       "Visit"
                     ],
                     "newValues": [
                       "Programmes/activities"
                     ]
                   },
                   {
                     "transactionType": "LOCATION_UPDATE_NON_RESI",
                     "attribute": "Non residential capacity",
                     "newValues": [
                       "99"
                     ]
                   }
                 ]
               }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can remove details of a locations of used by service`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(removeService))
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
              "servicesUsingLocation": [],
              "internalMovementAllowed": false
            }
          """,
            JsonCompareMode.LENIENT,
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
              "usage": [
                  {
                    "usageType": "PROPERTY",
                    "capacity": 10,
                    "sequence": 99
                  },
                  {
                    "usageType": "OTHER",
                    "capacity": 10,
                    "sequence": 99
                  }
              ],
              "servicesUsingLocation": [],
              "internalMovementAllowed": false,
              "changeHistory": [
                {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Use by service",
                  "oldValues": ["Official visits"]
                },
                 {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Usage",
                  "oldValues": ["Visit"]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can set internal movement using service type`() {
        webTestClient.patch().uri("/locations/non-residential/${visitRoom.id}")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(addInternalMovement))
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
              "servicesUsingLocation": [
                {
                  "serviceType": "INTERNAL_MOVEMENTS",
                  "serviceFamilyType": "INTERNAL_MOVEMENTS"
                }
              ],
              "internalMovementAllowed": true
            }
          """,
            JsonCompareMode.LENIENT,
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
              "usage": [
                {
                  "usageType": "MOVEMENT",
                  "capacity": 99,
                  "sequence": 99
                },
                {
                  "usageType": "PROPERTY",
                  "capacity": 10,
                  "sequence": 99
                },
                {
                  "usageType": "OTHER",
                  "capacity": 10,
                  "sequence": 99
                }
              ],
              "servicesUsingLocation": [
                {
                  "serviceType": "INTERNAL_MOVEMENTS",
                  "serviceFamilyType": "INTERNAL_MOVEMENTS"
                }
              ],
              "internalMovementAllowed":true,
              "changeHistory": [
                {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Use by service",
                  "oldValues": [
                    "Official visits"
                  ],
                  "newValues": [
                    "Internal movements"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Usage",
                  "oldValues": [
                    "Visit"
                  ],
                  "newValues": [
                    "Movement"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Non residential capacity",
                  "newValues": [
                    "99"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE_NON_RESI",
                  "attribute": "Internal movement allowed",
                  "oldValues": [
                    "false"
                  ],
                  "newValues": [
                    "true"
                  ]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
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
      servicesUsingLocation = setOf(ServiceType.APPOINTMENT),
    )

    val removeUsage = PatchNonResidentialLocationRequest(
      servicesUsingLocation = emptySet(),
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
              "servicesUsingLocation": [
              {
                "serviceType": "APPOINTMENT",
                "serviceFamilyType": "ACTIVITIES_APPOINTMENTS"
                }
              ],
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 99,
                  "sequence": 99
                },
                {
                  "usageType": "PROPERTY",
                  "capacity": 10,
                  "sequence": 99
                },
                {
                  "usageType": "OTHER",
                  "capacity": 10,
                  "sequence": 99
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${visitRoom.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
               {
                 "key": "MDI-Z-MEDICAL",
                 "servicesUsingLocation": [
                   {
                      "serviceType": "APPOINTMENT",
                      "serviceFamilyType": "ACTIVITIES_APPOINTMENTS"
                   }
                 ],
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 99,
                     "sequence": 99
                   },
                   {
                      "usageType": "PROPERTY",
                      "capacity": 10,
                      "sequence": 99
                    },
                    {
                      "usageType": "OTHER",
                      "capacity": 10,
                      "sequence": 99
                    }
                 ],
                 "changeHistory": [
                   {
                      "attribute": "Use by service",
                      "oldValues": ["Official visits"],
                      "newValues": ["Appointments"]
                    },
                    {
                      "attribute": "Usage",
                      "oldValues": ["Visit"],
                      "newValues": ["Appointment"]
                    },
                    {
                      "attribute": "Non residential capacity",
                      "newValues": ["99"]
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
              "usage": [
               {
                  "usageType": "PROPERTY",
                  "capacity": 10,
                  "sequence": 99
                },
                {
                  "usageType": "OTHER",
                  "capacity": 10,
                  "sequence": 99
                }
              ],
              "servicesUsingLocation": []
            }
          """,
            JsonCompareMode.LENIENT,
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
              "usage": [
                {
                  "usageType": "PROPERTY",
                  "capacity": 10,
                  "sequence": 99
                },
                {
                  "usageType": "OTHER",
                  "capacity": 10,
                  "sequence": 99
                }
              ],
              "changeHistory": [
                {
                  "attribute": "Use by service",
                  "oldValues": ["Official visits"]
                },
                {
                  "attribute": "Usage",
                  "oldValues": ["Visit"]
                }
              ]
            }
          """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /locations/prison/{prisonId}/non-residential")
  @Nested
  inner class GetNonResidentialLocationsTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.get().uri("/locations/prison/XXXYXT/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve all non-residential locations`() {
        webTestClient.get().uri("/locations/prison/${wingZ.prisonId}/non-residential")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                [
                  {
                    "key": "MDI-Z-ADJUDICATION",
                    "pathHierarchy": "Z-ADJUDICATION",
                    "locationType": "ADJUDICATION_ROOM",
                    "usage": [
                      {
                        "usageType": "ADJUDICATION_HEARING",
                        "capacity": 15,
                        "sequence": 1
                      }
                    ],
                    "servicesUsingLocation": [
                      {
                        "serviceType": "HEARING_LOCATION",
                        "serviceFamilyType": "ADJUDICATIONS"
                      }
                    ]
                  },
                  {
                    "key": "MDI-Z-VISIT",
                    "pathHierarchy": "Z-VISIT",
                    "locationType": "VISITS",
                    "usage": [
                      {
                        "usageType": "VISIT",
                        "capacity": 15,
                        "sequence": 1
                      },
                      {
                        "usageType": "PROPERTY",
                        "capacity": 10,
                        "sequence": 99
                      },
                      {
                        "usageType": "OTHER",
                        "capacity": 10,
                        "sequence": 99
                      }
                    ],
                    "servicesUsingLocation": [
                      {
                        "serviceType": "OFFICIAL_VISITS",
                        "serviceFamilyType": "OFFICIAL_VISITS"
                      }
                    ]
                  }
                ]
                         """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /locations/non-residential/summary/{prisonId}")
  @Nested
  inner class GetPagedNonResidentialLocationsTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.get().uri("/locations/non-residential/summary/XXXXXT")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve all non-residential locations`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                  "content": [
                    {
                      "localName": "Adjudication Room",
                      "code": "ADJUDICATION",
                      "pathHierarchy": "Z-ADJUDICATION",
                      "locationType": "ADJUDICATION_ROOM",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "ADJUDICATIONS"
                      ],
                      "usedByServices": [
                        "HEARING_LOCATION"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    },
                    {
                      "localName": "Visit Room",
                      "code": "VISIT",
                      "pathHierarchy": "Z-VISIT",
                      "locationType": "VISITS",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "usedByServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve all active non-residential locations`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?status=ACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                  "content": [
                    {
                      "localName": "Adjudication Room",
                      "code": "ADJUDICATION",
                      "pathHierarchy": "Z-ADJUDICATION",
                      "locationType": "ADJUDICATION_ROOM",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "ADJUDICATIONS"
                      ],
                      "usedByServices": [
                        "HEARING_LOCATION"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    },
                    {
                      "localName": "Visit Room",
                      "code": "VISIT",
                      "pathHierarchy": "Z-VISIT",
                      "locationType": "VISITS",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "usedByServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve non-residential locations matching wildcard names`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?localName=CAT")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                  "content": [
                    {
                      "localName": "Adjudication Room",
                      "code": "ADJUDICATION",
                      "pathHierarchy": "Z-ADJUDICATION",
                      "locationType": "ADJUDICATION_ROOM",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "ADJUDICATIONS"
                      ],
                      "usedByServices": [
                        "HEARING_LOCATION"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve all inactive non-residential locations`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?status=INACTIVE")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                  "content": [
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve all non-residential locations of a type`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?locationType=ADJUDICATION_ROOM")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                 "content": [
                    {
                      "localName": "Adjudication Room",
                      "code": "ADJUDICATION",
                      "pathHierarchy": "Z-ADJUDICATION",
                      "locationType": "ADJUDICATION_ROOM",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "ADJUDICATIONS"
                      ],
                      "usedByServices": [
                        "HEARING_LOCATION"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve all non-residential locations of a service type`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?serviceType=OFFICIAL_VISITS")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                "content": [
                    {
                      "localName": "Visit Room",
                      "code": "VISIT",
                      "pathHierarchy": "Z-VISIT",
                      "locationType": "VISITS",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "usedByServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }

      @Test
      fun `can retrieve a particular page of locations`() {
        webTestClient.get().uri("/locations/non-residential/summary/${wingZ.prisonId}?size=1&page=1")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "prisonId": "${wingZ.prisonId}",
                "locations": {
                "content": [
                    {
                      "localName": "Visit Room",
                      "code": "VISIT",
                      "pathHierarchy": "Z-VISIT",
                      "locationType": "VISITS",
                      "permanentlyInactive": false,
                      "usedByGroupedServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "usedByServices": [
                        "OFFICIAL_VISITS"
                      ],
                      "status": "ACTIVE",
                      "level": 2
                    }
                  ]
                }
              }
                 """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /locations/non-residential/prison/{prisonId}/service/{serviceType}")
  @Nested
  inner class ViewNonResidentialLocationsByServicesTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/service/OFFICIAL_VISITS")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/service/OFFICIAL_VISITS")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/service/OFFICIAL_VISITS")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve locations from service type`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/service/OFFICIAL_VISITS")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                [{
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "servicesUsingLocation": [
                    {
                      "serviceType": "OFFICIAL_VISITS",
                      "serviceFamilyType": "OFFICIAL_VISITS"
                    }
                  ],
                  "key": "MDI-Z-VISIT"
                }]
                         """,
            JsonCompareMode.LENIENT,
          )
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return client error for invalid usage type`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/service/UNKNOWN")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is4xxClientError
      }
    }
  }

  @DisplayName("GET /locations/non-residential/prison/{prisonId}/local-name/{localName}")
  @Nested
  inner class ViewNonResidentialLocationsByLocalNameTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/local-name/Visit Room")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/local-name/Visit Room")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/local-name/Visit Room")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `should return not found error for unknown local name`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/local-name/UNKNOWN")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve a location by local name`() {
        webTestClient.get().uri("/locations/non-residential/prison/${wingZ.prisonId}/local-name/Visit Room")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
                {
                  "prisonId": "MDI",
                  "localName":"Visit Room",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "key": "MDI-Z-VISIT"
                }
               """,
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("POST /locations/non-residential/prison/{prisonId}/generate-missing-children")
  @Nested
  inner class GenerateMissingChildrenTest {

    lateinit var gym: NonResidentialLocation
    lateinit var oldGym: NonResidentialLocation

    @BeforeEach
    fun setUp() {
      gym = repository.save(
        buildNonResidentialLocation(
          prisonId = "MDI",
          localName = "Gym",
          serviceType = ServiceType.APPOINTMENT,
        ).also {
          it.addChildLocation(
            buildNonResidentialLocation(
              prisonId = "MDI",
              localName = "Gym Area 1",
              locationType = LocationType.TRAINING_ROOM,
              serviceType = ServiceType.PROGRAMMES_AND_ACTIVITIES,
            ),
          )
        },
      )

      oldGym = repository.save(
        buildNonResidentialLocation(
          prisonId = "MDI",
          localName = "Gym Old",
          serviceType = ServiceType.USE_OF_FORCE,
          status = LocationStatus.INACTIVE,
        ).also {
          it.addChildLocation(
            buildNonResidentialLocation(
              prisonId = "MDI",
              localName = "Gym Old Old",
              serviceType = ServiceType.PROGRAMMES_AND_ACTIVITIES,
            ),
          )
        },
      )
    }

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/locations/non-residential/prison/MDI/generate-missing-children")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/non-residential/prison/MDI/generate-missing-children")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/non-residential/prison/MDI/generate-missing-children")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can generate missing child locations`() {
        val response = webTestClient.post().uri("/locations/non-residential/prison/MDI/generate-missing-children")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .exchange()
          .expectStatus().isCreated
          .expectBodyList<Location>()
          .returnResult().responseBody!!

        assertThat(response).hasSize(2)
        assertThat(response[0].localName).isEqualTo("Gym")
        assertThat(response[0].parentId).isEqualTo(gym.id)
        assertThat(response[0].servicesUsingLocation?.map { it.serviceType }).containsExactly(ServiceType.APPOINTMENT)
        assertThat(response[1].localName).isEqualTo("Gym Old")
        assertThat(response[1].parentId).isEqualTo(oldGym.id)
        assertThat(response[1].status).isEqualTo(DerivedLocationStatus.INACTIVE)
        assertThat(response[1].servicesUsingLocation?.map { it.serviceType }).containsExactly(ServiceType.USE_OF_FORCE)

        getDomainEvents(2).let {
          assertThat(it[0].eventType).isEqualTo("location.inside.prison.created")
          assertThat(it[0].additionalInformation?.id).isEqualTo(response[0].id)
          assertThat(it[1].eventType).isEqualTo("location.inside.prison.created")
          assertThat(it[1].additionalInformation?.id).isEqualTo(response[1].id)
        }
      }
    }
  }
}
