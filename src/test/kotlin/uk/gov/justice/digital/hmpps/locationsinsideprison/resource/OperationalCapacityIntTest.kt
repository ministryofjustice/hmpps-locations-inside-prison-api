package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class OperationalCapacityIntTest : SqsIntegrationTestBase() {

  @DisplayName("GET /signed-op-cap/MDI")
  @Nested
  inner class ViewLocationsConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/signed-op-cap/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve location-type constants`() {
        webTestClient.get().uri("/constants/location-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "locationTypes": [
                  {
                    "key": "WING",
                    "description": "Wing"
                  },
                  {
                    "key": "SPUR",
                    "description": "Spur"
                  },
                  {
                    "key": "LANDING",
                    "description": "Landing"
                  },
                  {
                    "key": "CELL",
                    "description": "Cell"
                  },
                  {
                    "key": "ROOM",
                    "description": "Room"
                  },
                  {
                    "key": "HOLDING_AREA",
                    "description": "Holding Area"
                  },
                  {
                    "key": "MOVEMENT_AREA",
                    "description": "Movement Area"
                  },
                  {
                    "key": "RESIDENTIAL_UNIT",
                    "description": "Residential Unit"
                  },
                  {
                    "key": "EXTERNAL_GROUNDS",
                    "description": "External Grounds"
                  },
                  {
                    "key": "HOLDING_CELL",
                    "description": "Holding Cell"
                  },
                  {
                    "key": "MEDICAL",
                    "description": "Medical"
                  },
                  {
                    "key": "GROUP",
                    "description": "Group"
                  },
                  {
                    "key": "OFFICE",
                    "description": "Other"
                  },
                  {
                    "key": "ADMINISTRATION_AREA",
                    "description": "Administration Area"
                  },
                  {
                    "key": "BOOTH",
                    "description": "Booth"
                  },
                  {
                    "key": "BOX",
                    "description": "Box"
                  },
                  {
                    "key": "RETURN_TO_UNIT",
                    "description": "Return to Unit"
                  },
                  {
                    "key": "CLASSROOM",
                    "description": "Classroom"
                  },
                  {
                    "key": "TRAINING_AREA",
                    "description": "Training Area"
                  },
                  {
                    "key": "TRAINING_ROOM",
                    "description": "Training Room"
                  },
                  {
                    "key": "EXERCISE_AREA",
                    "description": "Exercise Area"
                  },
                  {
                    "key": "AREA",
                    "description": "Area"
                  },
                  {
                    "key": "SPORTS",
                    "description": "Sports"
                  },
                  {
                    "key": "WORKSHOP",
                    "description": "Workshop"
                  },
                  {
                    "key": "INSIDE_PARTY",
                    "description": "Inside Party"
                  },
                  {
                    "key": "OUTSIDE_PARTY",
                    "description": "Outside Party"
                  },
                  {
                    "key": "FAITH_AREA",
                    "description": "Faith Area"
                  },
                  {
                    "key": "ADJUDICATION_ROOM",
                    "description": "Adjudication Room"
                  },
                  {
                    "key": "APPOINTMENTS",
                    "description": "Appointments"
                  },
                  {
                    "key": "VISITS",
                    "description": "Visits"
                  },
                  {
                    "key": "VIDEO_LINK",
                    "description": "Video Link"
                  },
                  {
                    "key": "ASSOCIATION",
                    "description": "Association"
                  },
                  {
                    "key": "INTERNAL_GROUNDS",
                    "description": "Internal Grounds"
                  },
                  {
                    "key": "INTERVIEW",
                    "description": "Interview"
                  },
                  {
                    "key": "LOCATION",
                    "description": "Location"
                  },
                  {
                    "key": "POSITION",
                    "description": "Position"
                  },
                  {
                    "key": "SHELF",
                    "description": "Shelf"
                  },
                  {
                    "key": "STORE",
                    "description": "Store"
                  },
                  {
                    "key": "TABLE",
                    "description": "Table"
                  }
                ]
              }
            """.trimIndent(),
            false,
          )
      }
    }
  }
}
