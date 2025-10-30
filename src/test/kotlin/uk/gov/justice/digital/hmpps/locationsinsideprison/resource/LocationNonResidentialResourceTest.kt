package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.CreateNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchResidentialLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

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
              "internalMovementAllowed": false,
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
                  "serviceName": "Hearing location",
                  "usageType": "ADJUDICATION_HEARING",
                  "serviceFamilyType": "ADJUDICATIONS",
                  "serviceFamilyName": "Adjudications"
                },
                {
                  "serviceType": "INTERNAL_MOVEMENTS",
                  "serviceName": "Internal movements",
                  "usageType": "MOVEMENT"
                }
              ],
              "internalMovementAllowed": true
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
                  "serviceName": "Appointments",
                  "usageType": "APPOINTMENT",
                  "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                  "serviceFamilyName": "Activities and appointments"
                }
              ],
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 99,
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
                      "serviceName": "Appointments",
                      "usageType": "APPOINTMENT",
                      "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                      "serviceFamilyName": "Activities and appointments"
                    }
                  ],
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 99,
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
              "usage": [],
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
                  "serviceName": "Internal movements",
                  "usageType": "MOVEMENT"
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
                }
              ],
              "servicesUsingLocation": [
                {
                  "serviceType": "INTERNAL_MOVEMENTS",
                  "serviceName": "Internal movements",
                  "usageType": "MOVEMENT"
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
                  "serviceName": "Appointments",
                  "usageType": "APPOINTMENT",
                  "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                  "serviceFamilyName": "Activities and appointments"
                }
              ],
              "usage": [
                {
                  "usageType": "APPOINTMENT",
                  "capacity": 99,
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
                     "serviceName": "Appointments",
                     "usageType": "APPOINTMENT",
                     "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                     "serviceFamilyName": "Activities and appointments"
                   }
                 ],
                 "usage": [
                   {
                     "usageType": "APPOINTMENT",
                     "capacity": 99,
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
              "usage": [],
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
              "usage": [],
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
                        "serviceName": "Hearing location",
                        "usageType": "ADJUDICATION_HEARING",
                        "serviceFamilyType": "ADJUDICATIONS",
                        "serviceFamilyName": "Adjudications"
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
                      }
                    ],
                    "servicesUsingLocation": [
                      {
                        "serviceType": "OFFICIAL_VISITS",
                        "serviceName": "Official visits",
                        "usageType": "VISIT"
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
                      "serviceName": "Official visits",
                      "usageType": "VISIT"
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
}
