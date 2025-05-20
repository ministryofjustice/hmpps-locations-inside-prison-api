package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.EXPECTED_USERNAME
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity as CapacityDto
@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationTransformResourceTest : CommonDataTestBase() {

  @DisplayName("PUT /locations/{id}/used-for-type")
  @Nested
  inner class UsedForTypeTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(UsedForType.STANDARD_ACCOMMODATION)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(UsedForType.STANDARD_ACCOMMODATION)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(UsedForType.STANDARD_ACCOMMODATION)))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update used-for-type as location is not found`() {
        webTestClient.put().uri("/locations/01908318-a677-7f6d-abe8-9c6daf5c3689/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(UsedForType.STANDARD_ACCOMMODATION)))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot update used-for-type as usedFor is not found in set`() {
        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""["TANNING_SALON"]""")
          .exchange()
          .expectStatus().isEqualTo(400)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update Use for type to a value successfully`() {
        val expectedUsedFor = setOf(UsedForType.PERSONALITY_DISORDER)

        val result = webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(UsedForType.PERSONALITY_DISORDER)))
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.usedFor == expectedUsedFor)

        val landingZ1 = result.findByPathHierarchy("Z-1")!!
        assertThat(landingZ1.usedFor == expectedUsedFor)

        val cellZ1001 = result.findByPathHierarchy("Z-1-001")!!
        assertThat(cellZ1001.usedFor == expectedUsedFor)

        val cellZ1002 = result.findByPathHierarchy("Z-1-002")!!
        assertThat(cellZ1002.usedFor == expectedUsedFor)

        val landingZ2 = result.findByPathHierarchy("Z-2")!!
        assertThat(landingZ2.usedFor!!.isEmpty())

        val cellVisit = result.findByPathHierarchy("Z-VISIT")
        assertThat(cellVisit == null)

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1-01S",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }

      @Test
      fun `can update Use for type to no value successfully`() {
        val result = webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("[]")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.usedFor!!.isEmpty())

        val landingZ1 = result.findByPathHierarchy("Z-1")!!
        assertThat(landingZ1.usedFor!!.isEmpty())

        val cellZ1001 = result.findByPathHierarchy("Z-1-001")!!
        assertThat(cellZ1001.usedFor!!.isEmpty())

        val cellZ1002 = result.findByPathHierarchy("Z-1-002")!!
        assertThat(cellZ1002.usedFor!!.isEmpty())

        val landingZ2 = result.findByPathHierarchy("Z-2")!!
        assertThat(landingZ2.usedFor!!.isEmpty())

        val cellVisit = result.findByPathHierarchy("Z-VISIT")
        assertThat(cellVisit == null)

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1-01S",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }

      @Test
      fun `can update Use for type to two values successfully`() {
        val expectedTypes =

          webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(listOf(UsedForType.FIRST_NIGHT_CENTRE, UsedForType.PERSONALITY_DISORDER, UsedForType.STANDARD_ACCOMMODATION)))
            .exchange()
            .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(listOf(UsedForType.FIRST_NIGHT_CENTRE, UsedForType.HIGH_SECURITY)))
          .exchange()
          .expectStatus().isOk

        val result = webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("[]")
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.usedFor == expectedTypes)

        val landingZ1 = result.findByPathHierarchy("Z-1")!!
        assertThat(landingZ1.usedFor == expectedTypes)

        val cellZ1001 = result.findByPathHierarchy("Z-1-001")!!
        assertThat(cellZ1001.usedFor == expectedTypes)

        val cellZ1002 = result.findByPathHierarchy("Z-1-002")!!
        assertThat(cellZ1002.usedFor == expectedTypes)

        val landingZ2 = result.findByPathHierarchy("Z-2")!!
        assertThat(landingZ2.usedFor!!.isEmpty())

        val cellVisit = result.findByPathHierarchy("Z-VISIT")
        assertThat(cellVisit == null)

        getDomainEvents(6).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1-01S",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${landingZ1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
              "key": "MDI-Z-1",
              "changeHistory": [
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "Personality disorder unit",
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "Standard accommodation",
                    "First night centre / Induction",
                    "Personality disorder unit"
                  ]
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${landingZ2.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
              "key": "MDI-Z-2",
              "changeHistory": []
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${wingZ.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
              "key": "MDI-Z",
              "changeHistory": [
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "Personality disorder unit",
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "Standard accommodation",
                    "First night centre / Induction",
                    "Personality disorder unit"
                  ]
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )

        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
              "key": "MDI-Z-1-001",
              "changeHistory": [
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "First night centre / Induction",
                    "Personality disorder unit",
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "First night centre / Induction",
                    "High security unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_UPDATE",
                  "attribute": "Used for",
                  "oldValues": [
                    "Standard accommodation"
                  ],
                  "newValues": [
                    "Standard accommodation",
                    "First night centre / Induction",
                    "Personality disorder unit"
                  ]
                },
                {
                  "transactionType": "LOCATION_CREATE",
                  "attribute": "Used for",
                  "newValues": [
                    "Standard accommodation"
                  ],
                  "amendedBy": "A_TEST_USER"
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/specialist-cell-types")
  @Nested
  inner class SetSpecialistCellTypesTest {
    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("MAINTAIN_LOCATIONS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update specialists cell types as location is not found`() {
        webTestClient.put().uri("/locations/01908318-a677-7f6d-abe8-9c6daf5c3689/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot update specialists cell types as location is not a cell`() {
        webTestClient.put().uri("/locations/${wingZ.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot remove specialists cell types if capacity is 0 for normal accommodations`() {
        val testCell = repository.save(
          buildCell(
            pathHierarchy = "Z-1-005",
            capacity = Capacity(maxCapacity = 2, workingCapacity = 0),
            certification = Certification(certified = true, capacityOfCertifiedCell = 2),
            specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
            linkedTransaction = linkedTransaction,
          ),
        )

        assertThat(
          webTestClient.put().uri("/locations/${testCell.id}/specialist-cell-types")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(jsonString(emptySet<SpecialistCellType>()))
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(106)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update specialist cell types successfully`() {
        val expectedSpecialistCell = setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)

        val result = webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST)))
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.specialistCellTypes!! == expectedSpecialistCell)

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
          )
        }
      }

      @Test
      fun `can remove specialist cell types successfully`() {
        val specialistCellTypes = null

        val result = webTestClient.put().uri("/locations/${cell2.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(emptySet<SpecialistCellType>()))
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.specialistCellTypes == specialistCellTypes)

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-002",
          )
        }
      }

      @Test
      fun `can update specialist cell types and record history with changes as a list`() {
        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.BIOHAZARD_DIRTY_PROTEST, SpecialistCellType.SAFE_CELL, SpecialistCellType.CONSTANT_SUPERVISION)))
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.MEDICAL, SpecialistCellType.SAFE_CELL)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.DRY, SpecialistCellType.MEDICAL)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(setOf(SpecialistCellType.DRY)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${cell1.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(emptySet<SpecialistCellType>()))
          .exchange()
          .expectStatus().isOk

        webTestClient.get().uri("/locations/${cell1.id}?includeHistory=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
              "key": "MDI-Z-1-001",
              "changeHistory": [
                {
                  "attribute": "Cell type",
                  "oldValues": [
                    "Dry cell"
                  ],
                  "transactionType": "CELL_TYPE_CHANGES"
                },
                {
                  "attribute": "Cell type",
                  "oldValues": [
                    "Medical cell",
                    "Dry cell"
                  ],
                  "newValues": [
                    "Dry cell"
                  ],
                  "transactionType": "CELL_TYPE_CHANGES"
                },
                {
                  "attribute": "Cell type",
                  "oldValues": [
                    "Safe cell",
                    "Medical cell"
                  ],
                  "newValues": [
                    "Medical cell",
                    "Dry cell"
                  ],
                  "transactionType": "CELL_TYPE_CHANGES"
                },
                {
                  "attribute": "Cell type",
                  "oldValues": [
                    "Safe cell",
                    "Biohazard / dirty protest cell",
                    "Constant supervision cell"
                  ],
                  "newValues": [
                    "Safe cell",
                    "Medical cell"
                  ],
                  "transactionType": "CELL_TYPE_CHANGES"
                },
                {
                  "attribute": "Cell type",
                  "newValues": [
                    "Safe cell",
                    "Constant supervision cell",
                    "Biohazard / dirty protest cell"
                  ],
                  "transactionType": "CELL_TYPE_CHANGES"
                },
                {
                  "attribute": "Used for",
                  "newValues": ["Standard accommodation"],
                  "transactionType": "LOCATION_CREATE"
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/capacity")
  @Nested
  inner class CapacityChangeTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDto()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDto()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(CapacityDto()))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/capacity")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                CapacityDto(
                  workingCapacity = -1,
                  maxCapacity = 999,
                ),
              ),
            )
            .exchange()
            .expectStatus().is4xxClientError
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(102)
      }

      @Test
      fun `cannot reduce max capacity of a cell below number of prisoners in cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell1.getPathHierarchy()), true)

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/capacity")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                CapacityDto(
                  workingCapacity = 1,
                  maxCapacity = 1,
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(117)
      }

      @Test
      fun `cannot have a max cap below a working cap`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/capacity")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                CapacityDto(
                  workingCapacity = 3,
                  maxCapacity = 2,
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(114)
      }

      @Test
      fun `cannot have a max cap of 0`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        cell1.accommodationType = AccommodationType.CARE_AND_SEPARATION
        repository.save(cell1)
        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/capacity")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                CapacityDto(
                  workingCapacity = 0,
                  maxCapacity = 0,
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(ErrorCode.MaxCapacityCannotBeZero.errorCode)
      }

      @Test
      fun `cannot have a working cap = 0 when accommodation type = normal accommodation and not a specialist cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        assertThat(
          webTestClient.put().uri("/locations/${cell1.id}/capacity")
            .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
            .header("Content-Type", "application/json")
            .bodyValue(
              jsonString(
                CapacityDto(
                  workingCapacity = 0,
                  maxCapacity = 2,
                ),
              ),
            )
            .exchange()
            .expectStatus().isEqualTo(400)
            .expectBody(ErrorResponse::class.java)
            .returnResult().responseBody!!.errorCode,
        ).isEqualTo(106)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can change the capacity of a cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = 1,
                maxCapacity = 2,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "${cell1.id}",
                "prisonId": "MDI",
                "code": "001",
                "pathHierarchy": "Z-1-001",
                "locationType": "CELL",
                "accommodationTypes": [
                  "NORMAL_ACCOMMODATION"
                ],
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 1
                },
                "certification": {
                  "certified": true
                },
                "active": true,
                "isResidential": true,
                "key": "MDI-Z-1-001"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }

      @Test
      fun `can change the capacity of CSWAP`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cswap.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cswap.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              CapacityDto(
                workingCapacity = 90,
                maxCapacity = 95,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "${cswap.id}",
                "prisonId": "MDI",
                "code": "CSWAP",
                "pathHierarchy": "CSWAP",
                "locationType": "AREA",
                "capacity": {
                  "maxCapacity": 95,
                  "workingCapacity": 90
                },
                "active": true,
                "isResidential": false,
                "key": "MDI-CSWAP"
              }
          """,
            JsonCompareMode.LENIENT,
          )

        getDomainEvents(1).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-CSWAP",
          )
        }
      }

      @Test
      fun `can change the max capacity of a cell for an certification approval required prison`() {
        val aCell = leedsWing.cellLocations().find { it.getKey() == "LEI-A-1-001" } ?: throw RuntimeException("Cell not found")
        prisonerSearchMockServer.stubSearchByLocations("LEI", listOf(aCell.getPathHierarchy()), false)

        val incMaxCap = aCell.getMaxCapacity()?.inc() ?: 1
        webTestClient.put().uri("/locations/${aCell.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = aCell.getWorkingCapacity() ?: 0,
                maxCapacity = incMaxCap,
              ),
            ),
          )
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "key": "${aCell.getKey()}",
                "id": "${aCell.id}",
                "prisonId": "${aCell.prisonId}",
                "code": "${aCell.getCode()}",
                "pathHierarchy": "${aCell.getPathHierarchy()}",
                "capacity": {
                  "maxCapacity": ${aCell.getMaxCapacity()},
                  "workingCapacity": ${aCell.getWorkingCapacity()}
                },
                "pendingCapacity": {
                  "maxCapacity": $incMaxCap,
                  "workingCapacity": ${aCell.getWorkingCapacity()}
                },
                "certification": {
                  "certified": true
                },
                "status": "LOCKED_ACTIVE"

              }
          """,
            JsonCompareMode.LENIENT,
          )

        assertThat(getNumberOfMessagesCurrentlyOnQueue()).isEqualTo(0)
      }
    }
  }
}
