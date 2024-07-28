package uk.gov.justice.digital.hmpps.locationsinsideprison.resource.locationresource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationTest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Capacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Cell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Certification
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LocationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildCell
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildNonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.buildResidentialLocation
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser

@WithMockAuthUser(username = EXPECTED_USERNAME)
class LocationResourceTransformTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var repository: LocationRepository
  lateinit var cell1: Cell
  lateinit var cell2: Cell
  lateinit var inactiveCellB3001: Cell
  lateinit var archivedCell: Cell
  lateinit var landingZ1: ResidentialLocation
  lateinit var landingZ2: ResidentialLocation
  lateinit var landingB3: ResidentialLocation
  lateinit var landingN1: ResidentialLocation
  lateinit var wingZ: ResidentialLocation
  lateinit var wingB: ResidentialLocation
  lateinit var wingN: ResidentialLocation
  lateinit var visitRoom: NonResidentialLocation
  lateinit var adjRoom: NonResidentialLocation

  @BeforeEach
  fun setUp() {
    prisonerSearchMockServer.resetAll()
    repository.deleteAll()

    wingN = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A",
        locationType = LocationType.WING,
        localName = "WING A",
      ),
    )

    landingN1 = repository.save(
      buildResidentialLocation(
        prisonId = "NMI",
        pathHierarchy = "A-1",
        locationType = LocationType.LANDING,
        localName = "LANDING A",
      ),
    )

    wingN.addChildLocation(landingN1)
    repository.save(wingN)

    wingZ = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z",
        locationType = LocationType.WING,
      ),
    )
    wingB = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B",
        locationType = LocationType.WING,
      ),
    )
    landingZ1 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-1",
        locationType = LocationType.LANDING,
      ),
    )
    landingZ2 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "Z-2",
        locationType = LocationType.LANDING,
      ),
    )
    landingB3 = repository.save(
      buildResidentialLocation(
        pathHierarchy = "B-A",
        locationType = LocationType.LANDING,
      ),
    )
    cell1 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-001",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
      ),
    )
    cell2 = repository.save(
      buildCell(
        pathHierarchy = "Z-1-002",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        residentialAttributeValues = setOf(
          ResidentialAttributeValue.CAT_A,
          ResidentialAttributeValue.SAFE_CELL,
          ResidentialAttributeValue.DOUBLE_OCCUPANCY,
        ),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      ),
    )
    inactiveCellB3001 = repository.save(
      buildCell(
        pathHierarchy = "B-A-001",
        active = false,
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        specialistCellType = SpecialistCellType.ACCESSIBLE_CELL,
      ),
    )

    archivedCell = repository.save(
      buildCell(
        pathHierarchy = "Z-1-003",
        capacity = Capacity(maxCapacity = 2, workingCapacity = 2),
        certification = Certification(certified = true, capacityOfCertifiedCell = 2),
        active = false,
        archived = true,
      ),
    )

    visitRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "VISIT",
        locationType = LocationType.VISITS,
        nonResidentialUsageType = NonResidentialUsageType.VISIT,
      ),
    )
    adjRoom = repository.save(
      buildNonResidentialLocation(
        pathHierarchy = "ADJUDICATION",
        locationType = LocationType.ADJUDICATION_ROOM,
        nonResidentialUsageType = NonResidentialUsageType.ADJUDICATION_HEARING,
      ),
    )
    wingZ.addChildLocation(visitRoom)
      .addChildLocation(
        landingZ1
          .addChildLocation(cell1)
          .addChildLocation(cell2),
      )
      .addChildLocation(landingZ2)

    wingZ.updateComments(
      "A New Comment",
      uk.gov.justice.digital.hmpps.locationsinsideprison.resource.locationresource.EXPECTED_USERNAME,
      clock,
    )

    wingB.addChildLocation(landingB3.addChildLocation(inactiveCellB3001))
    repository.save(wingZ)
    repository.save(wingB)
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
}
