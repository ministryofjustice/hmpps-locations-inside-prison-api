package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PermanentDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.TemporaryDeactivationLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataIntegrationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import java.time.LocalDate
import java.time.LocalDateTime

class LocationResourcePutTest : CommonDataIntegrationTest() {

  @DisplayName("PUT /locations/{id}/convert-cell-to-non-res-cell")
  @Nested
  inner class ConvertCellToNonResCell {

    var convertCellToNonResidentialLocationRequest = ConvertCellToNonResidentialLocationRequest(
      convertedCellType = ConvertedCellType.OTHER,
      otherConvertedCellType = "Taning room",
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update convert to non residential cell as ID is not found`() {
        webTestClient.put().uri("/locations/${java.util.UUID.randomUUID()}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertCellToNonResidentialLocationRequest))
          .exchange()
          .expectStatus().isEqualTo(404)
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can update convert cell to non res cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell1.getPathHierarchy()), false)
        val result = webTestClient.put().uri("/locations/${cell1.id}/convert-cell-to-non-res-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertCellToNonResidentialLocationRequest)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        assertThat(result.findByPathHierarchy("Z-1-001")!!.convertedCellType == ConvertedCellType.OTHER)

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/{id}/convert-to-cell")
  @Nested
  inner class ConvertToCellTest {
    var convertToCellRequest = ConvertToCellRequest(
      accommodationType = AccommodationType.NORMAL_ACCOMMODATION,
      specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      maxCapacity = 2,
      workingCapacity = 2,
    )

    var convertToCellRequestNotValidMaxCapacity = ConvertToCellRequest(
      accommodationType = AccommodationType.CARE_AND_SEPARATION,
      specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      maxCapacity = -1,
      workingCapacity = 2,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    var convertToCellRequestNotValidWorkingCapacity = ConvertToCellRequest(
      accommodationType = AccommodationType.CARE_AND_SEPARATION,
      specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      maxCapacity = 1,
      workingCapacity = -1,
      usedForTypes = listOf(UsedForType.STANDARD_ACCOMMODATION, UsedForType.PERSONALITY_DISORDER),
    )

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"prisonId": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot update convert to cell as Location ID is not found`() {
        webTestClient.put().uri("/locations/${java.util.UUID.randomUUID()}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(convertToCellRequest))
          .exchange()
          .expectStatus().isEqualTo(404)
      }

      @Test
      fun `cannot update convert to cell as request has invalid Max Capacity `() {
        cell1.convertToNonResidentialCell(convertedCellType = ConvertedCellType.OTHER, userOrSystemInContext = "Aleman", clock = clock)
        repository.save(cell1)
        // request has not valid data
        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequestNotValidMaxCapacity)
          .exchange()
          .expectStatus().isEqualTo(400)
      }

      @Test
      fun `cannot update convert to cell as request has invalid Working Capacity `() { // request has not valid data
        cell1.convertToNonResidentialCell(convertedCellType = ConvertedCellType.OTHER, userOrSystemInContext = "Aleman", clock = clock)
        repository.save(cell1)

        webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequestNotValidWorkingCapacity)
          .exchange()
          .expectStatus().isEqualTo(400)
      }
    }

    @Nested
    inner class HappyPath {

      // TODO MAP-1438 extra couple of tests need to be added for "Healthcare inpatients" & "Care and separation"
      @Test
      fun `can convert non-res cell to res cell`() {
        cell1.convertToNonResidentialCell(convertedCellType = ConvertedCellType.OTHER, userOrSystemInContext = "Aleman", clock = clock)
        repository.save(cell1)

        val result = webTestClient.put().uri("/locations/${cell1.id}/convert-to-cell")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(convertToCellRequest)
          .exchange()
          .expectStatus().isOk
          .expectBody(LocationTest::class.java)
          .returnResult().responseBody!!

        val cellZ1001 = result.findByPathHierarchy("Z-1-001")
        assertThat(cellZ1001?.capacity?.maxCapacity).isEqualTo(2)
        assertThat(cellZ1001?.capacity?.workingCapacity).isEqualTo(2)
        assertThat(cellZ1001?.specialistCellTypes?.get(0)).isEqualTo(SpecialistCellType.ACCESSIBLE_CELL)
        assertThat(cellZ1001?.convertedCellType).isNotEqualTo("OTHER")

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

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

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
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

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }

      @Test
      fun `can update Use for type to two values successfully`() {
        val expectedTypes = listOf(UsedForType.FIRST_NIGHT_CENTRE, UsedForType.PERSONALITY_DISORDER)

        val result = webTestClient.put().uri("/locations/${wingZ.id}/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(expectedTypes))
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

        getDomainEvents(5).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-002",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z-2",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
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
          ),
        )

        webTestClient.put().uri("/locations/${testCell.id}/specialist-cell-types")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(emptySet<SpecialistCellType>()))
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody().json(
            """
              { "errorCode": 106 }
            """.trimIndent(),
            false,
          )
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
          .bodyValue(jsonString(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity()))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = -1,
                maxCapacity = 999,
              ),
            ),
          )
          .exchange()
          .expectStatus().is4xxClientError
          .expectBody().json(
            """
              { "errorCode": 102 }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `cannot reduce max capacity of a cell below number of prisoners in cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell1.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = 1,
                maxCapacity = 1,
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody().json(
            """
              { "errorCode": 117 }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `cannot have a max cap below a working cap`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = 3,
                maxCapacity = 2,
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody().json(
            """
              { "errorCode": 114 }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `cannot have a max cap of 0`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = 0,
                maxCapacity = 0,
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody().json(
            """
              { "errorCode": 115 }
            """.trimIndent(),
            false,
          )
      }

      @Test
      fun `cannot have a working cap = 0 when accommodation type = normal accommodation and not a specialist cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${cell1.id}/capacity")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(
            jsonString(
              uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Capacity(
                workingCapacity = 0,
                maxCapacity = 2,
              ),
            ),
          )
          .exchange()
          .expectStatus().isEqualTo(400)
          .expectBody().json(
            """
              { "errorCode": 106 }
            """.trimIndent(),
            false,
          )
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
            false,
          )

        getDomainEvents(3).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }
      }
    }
  }

  @DisplayName("PUT /locations/{id}/deactivate")
  @Nested
  inner class TemporarilyDeactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `access client error bad data`() {
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue("""{"deactivationReason": ""}""")
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `cannot deactivate a location when prisoner is inside the cell`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
          .exchange()
          .expectStatus().isEqualTo(409)
      }

      @Test
      fun `cannot deactivate a wing when prisoners are in cells below`() {
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), true)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Demolished")))
          .exchange()
          .expectStatus().isEqualTo(409)
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can deactivate a location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/permanent")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(PermanentDeactivationLocationRequest(reason = "Cell destroyed")))
          .exchange()
          .expectStatus().isOk

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
              {
                "id": "${wingZ.id}",
                "prisonId": "MDI",
                "code": "Z",
                "pathHierarchy": "Z",
                "locationType": "WING",
                "accommodationTypes": [ "NORMAL_ACCOMMODATION" ],
                "capacity": {
                  "maxCapacity": 2,
                  "workingCapacity": 0
                },
                "certification": {
                  "certified": true,
                  "capacityOfCertifiedCell": 2
                },
                "active": false,
                "deactivatedDate": "$now",
                "deactivatedReason": "DAMAGED",
                "permanentlyInactive": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "topLevelId": "${wingZ.id}",
                "isResidential": true,
                "key": "MDI-Z",
                "deactivatedBy": "LOCATIONS_INSIDE_PRISON_API"
              }
          """,
            false,
          )

        getDomainEvents(8).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": false,
              "deactivatedByParent": false,
              "key": "MDI-Z",
              "deactivatedReason": "DAMAGED",
              "accommodationTypes":["NORMAL_ACCOMMODATION"],
              "permanentlyInactive": false,
              "proposedReactivationDate": "$proposedReactivationDate",
              "deactivatedDate": "$now",
              "capacity": {
                "maxCapacity": 2,
                "workingCapacity": 0
              },
              "certification": {
                "certified": true,
                "capacityOfCertifiedCell": 2
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": false,
                  "deactivatedByParent": true,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": false,
                  "deactivatedByParent": false,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "permanentlyInactive": false,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": false,
                      "deactivatedByParent": false,
                      "proposedReactivationDate": "$proposedReactivationDate",
                      "deactivatedDate": "$now",
                      "deactivatedReason": "DAMAGED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": false,
                  "deactivatedByParent": false,
                  "proposedReactivationDate": "$proposedReactivationDate",
                  "deactivatedDate": "$now",
                  "deactivatedReason": "DAMAGED",
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can update a deactivated location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val now = LocalDateTime.now(clock)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED)))
          .exchange()
          .expectStatus().isOk

        val proposedReactivationDate = now.plusMonths(1).toLocalDate()
        webTestClient.put().uri("/locations/${cell1.id}/update/temporary-deactivation")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED, planetFmReference = "334423", proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        getDomainEvents(4).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1-001",
          )
        }

        webTestClient.get().uri("/locations/${cell1.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
                "prisonId": "MDI",
                "code": "001",
                "pathHierarchy": "Z-1-001",
                "locationType": "CELL",
                "accommodationTypes":["NORMAL_ACCOMMODATION"],
                "active": false,
                "deactivatedByParent": false,
                "proposedReactivationDate": "$proposedReactivationDate",
                "deactivatedDate": "$now",
                "deactivatedReason": "MOTHBALLED",
                "planetFmReference": "334423",
                "isResidential": true,
                "key": "MDI-Z-1-001"
            }
          """,
            false,
          )
      }
    }
  }

  @DisplayName("PUT /locations/{id}/reactivate")
  @Nested
  inner class ReactivateLocationTest {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf()))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with right role, wrong scope`() {
        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS"), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can cascade reactivated locations`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy(), cell2.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${wingZ.id}/reactivate?cascade-reactivation=true")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(12).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.reactivated" to "MDI-Z",
            "location.inside.prison.reactivated" to "MDI-Z-1",
            "location.inside.prison.reactivated" to "MDI-Z-2",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.reactivated" to "MDI-Z-1-002",
            "location.inside.prison.reactivated" to "MDI-Z-VISIT",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 4
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }

      @Test
      fun `can reactivate a location`() {
        prisonerSearchMockServer.stubSearchByLocations(cell1.prisonId, listOf(cell1.getPathHierarchy()), false)

        val proposedReactivationDate = LocalDate.now(clock).plusMonths(1)
        webTestClient.put().uri("/locations/${cell1.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.DAMAGED, proposedReactivationDate = proposedReactivationDate)))
          .exchange()
          .expectStatus().isOk

        prisonerSearchMockServer.resetAll()
        prisonerSearchMockServer.stubSearchByLocations(wingZ.prisonId, listOf(cell2.getPathHierarchy(), cell1.getPathHierarchy()), false)

        webTestClient.put().uri("/locations/${wingZ.id}/deactivate/temporary")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .bodyValue(jsonString(TemporaryDeactivationLocationRequest(deactivationReason = DeactivatedReason.MOTHBALLED)))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/locations/${cell1.id}/reactivate")
          .headers(setAuthorisation(roles = listOf("ROLE_MAINTAIN_LOCATIONS"), scopes = listOf("write")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        getDomainEvents(12).let {
          assertThat(it.map { message -> message.eventType to message.additionalInformation?.key }).containsExactlyInAnyOrder(
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z",
            "location.inside.prison.deactivated" to "MDI-Z-1",
            "location.inside.prison.deactivated" to "MDI-Z-2",
            "location.inside.prison.deactivated" to "MDI-Z-1-001",
            "location.inside.prison.deactivated" to "MDI-Z-1-002",
            "location.inside.prison.deactivated" to "MDI-Z-VISIT",
            "location.inside.prison.reactivated" to "MDI-Z-1-001",
            "location.inside.prison.amended" to "MDI-Z-1",
            "location.inside.prison.amended" to "MDI-Z",
          )
        }

        webTestClient.get().uri("/locations/${wingZ.id}?includeChildren=true")
          .headers(setAuthorisation(roles = listOf("ROLE_VIEW_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            // language=json
            """
             {
              "prisonId": "MDI",
              "code": "Z",
              "pathHierarchy": "Z",
              "locationType": "WING",
              "active": true,
              "key": "MDI-Z",
              "capacity": {
                "maxCapacity": 4,
                "workingCapacity": 2
              },
              "certification": {
                "capacityOfCertifiedCell": 4
              },
              "childLocations": [
                {
                  "prisonId": "MDI",
                  "code": "VISIT",
                  "pathHierarchy": "Z-VISIT",
                  "locationType": "VISITS",
                  "active": true,
                  "isResidential": false,
                  "key": "MDI-Z-VISIT"
                },
                {
                  "prisonId": "MDI",
                  "code": "1",
                  "pathHierarchy": "Z-1",
                  "locationType": "LANDING",
                  "accommodationTypes":["NORMAL_ACCOMMODATION"],
                  "active": true,
                  "isResidential": true,
                  "key": "MDI-Z-1",
                  "childLocations": [
                    {
                      "prisonId": "MDI",
                      "code": "001",
                      "pathHierarchy": "Z-1-001",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": true,
                      "isResidential": true,
                      "key": "MDI-Z-1-001"
                    },
                    {
                      "prisonId": "MDI",
                      "code": "002",
                      "pathHierarchy": "Z-1-002",
                      "locationType": "CELL",
                      "accommodationTypes":["NORMAL_ACCOMMODATION"],
                      "active": false,
                      "deactivatedReason": "MOTHBALLED",
                      "isResidential": true,
                      "key": "MDI-Z-1-002"
                    }
                  ]
                },
                {
                  "prisonId": "MDI",
                  "code": "2",
                  "pathHierarchy": "Z-2",
                  "locationType": "LANDING",
                  "accommodationTypes":[],
                  "active": false,
                  "deactivatedReason": "MOTHBALLED",
                  "isResidential": true,
                  "key": "MDI-Z-2"
                }
              ]
            }
          """,
            false,
          )
      }
    }
  }
}
