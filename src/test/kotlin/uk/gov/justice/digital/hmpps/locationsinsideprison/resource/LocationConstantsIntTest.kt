package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class LocationConstantsIntTest : SqsIntegrationTestBase() {

  @BeforeEach
  fun setUp() {
    prisonRegisterMockServer.resetAll()
  }

  @DisplayName("GET /constants/location-type")
  @Nested
  inner class ViewLocationsConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/location-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/location-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/location-type")
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
                    "description": "Holding area"
                  },
                  {
                    "key": "MOVEMENT_AREA",
                    "description": "Movement area"
                  },
                  {
                    "key": "RESIDENTIAL_UNIT",
                    "description": "Residential unit"
                  },
                  {
                    "key": "EXTERNAL_GROUNDS",
                    "description": "External grounds"
                  },
                  {
                    "key": "HOLDING_CELL",
                    "description": "Holding cell"
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
                    "description": "Office"
                  },
                  {
                    "key": "ADMINISTRATION_AREA",
                    "description": "Administration area"
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
                    "description": "Return to unit"
                  },
                  {
                    "key": "CLASSROOM",
                    "description": "Classroom"
                  },
                  {
                    "key": "TRAINING_AREA",
                    "description": "Training area"
                  },
                  {
                    "key": "TRAINING_ROOM",
                    "description": "Training room"
                  },
                  {
                    "key": "EXERCISE_AREA",
                    "description": "Exercise area"
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
                    "description": "Inside party"
                  },
                  {
                    "key": "OUTSIDE_PARTY",
                    "description": "Outside party"
                  },
                  {
                    "key": "FAITH_AREA",
                    "description": "Faith area"
                  },
                  {
                    "key": "ADJUDICATION_ROOM",
                    "description": "Adjudication room"
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
                    "description": "Video link"
                  },
                  {
                    "key": "ASSOCIATION",
                    "description": "Association"
                  },
                  {
                    "key": "INTERNAL_GROUNDS",
                    "description": "Internal grounds"
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
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /constants/deactivated-reason")
  @Nested
  inner class ViewDeactivatedReasonsConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/deactivated-reason")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/deactivated-reason")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/deactivated-reason")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve deactivated-reasons constants`() {
        webTestClient.get().uri("/constants/deactivated-reason")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
                "deactivatedReasons": [
                  {
                    "key": "DAMAGED",
                    "description": "Damage"
                  },
                  {
                    "key": "DAMP",
                    "description": "Damp / mould"
                  },
                  {
                    "key": "MAINTENANCE",
                    "description": "Maintenance"
                  },
                  {
                    "key": "MOTHBALLED",
                    "description": "Mothballed"
                  },
                  {
                    "key": "PEST",
                    "description": "Pest control"
                  },
                  {
                    "key": "REFURBISHMENT",
                    "description": "Refurbishment"
                  },
                  {
                    "key": "SECURITY_SEALED",
                    "description": "Security sealed"
                  },
                  {
                    "key": "STAFF_SHORTAGE",
                    "description": "Staff shortage"
                  },
                  {
                    "key": "OTHER",
                    "description": "Other"
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/residential-housing-type")
  @Nested
  inner class ViewResidentialHousingTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/residential-housing-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/residential-housing-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/residential-housing-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve residential-housing-type constants`() {
        webTestClient.get().uri("/constants/residential-housing-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "residentialHousingTypes": [
                  {
                    "key": "HEALTHCARE",
                    "description": "Healthcare"
                  },
                  {
                    "key": "HOLDING_CELL",
                    "description": "Holding Cell"
                  },
                  {
                    "key": "NORMAL_ACCOMMODATION",
                    "description": "Normal Accommodation"
                  },                  {
                    "key": "RECEPTION",
                    "description": "Reception"
                  },
                  {
                    "key": "SEGREGATION",
                    "description": "Segregation"
                  },
                  {
                    "key": "SPECIALIST_CELL",
                    "description": "Specialist Cell"
                  },
                  {
                    "key": "OTHER_USE",
                    "description": "Other Use"
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/non-residential-usage-type")
  @Nested
  inner class ViewNonResidentialUsageTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/non-residential-usage-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/non-residential-usage-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/non-residential-usage-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve non-residential-usage-type constants`() {
        webTestClient.get().uri("/constants/non-residential-usage-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
         {
           "nonResidentialUsageTypes": [
             {
               "key": "ADJUDICATION_HEARING",
               "description": "Adjudication hearing"
             },
             {
               "key": "APPOINTMENT",
               "description": "Appointment"
             },
             {
               "key": "MOVEMENT",
               "description": "Movement"
             },
             {
               "key": "OCCURRENCE",
               "description": "Occurrence"
             },
             {
               "key": "PROGRAMMES_ACTIVITIES",
               "description": "Programmes/activities"
             },
             {
               "key": "PROPERTY",
               "description": "Property"
             },
             {
               "key": "VISIT",
               "description": "Visit"
             },             {
               "key": "OTHER",
               "description": "Other"
             }
           ]
         }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/residential-attribute-type")
  @Nested
  inner class ViewResidentialAttributeTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/residential-attribute-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/residential-attribute-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/residential-attribute-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve residential-attribute-type constants`() {
        webTestClient.get().uri("/constants/residential-attribute-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "residentialAttributeTypes": [
                  {
                    "key": "USED_FOR",
                    "description": "Used for",
                    "values": [
                      {
                        "key": "UNCONVICTED_JUVENILES",
                        "description": "Unconvicted Juveniles"
                      },
                      {
                        "key": "SENTENCED_JUVENILES",
                        "description": "Sentenced Juveniles"
                      },
                      {
                        "key": "UNCONVICTED_18_20",
                        "description": "Unconvicted 18 to 20 year olds"
                      },
                      {
                        "key": "SENTENCED_18_20",
                        "description": "Sentenced 18 to 20 year olds"
                      },
                      {
                        "key": "UNCONVICTED_ADULTS",
                        "description": "Unconvicted Adults"
                      },
                      {
                        "key": "SENTENCED_ADULTS",
                        "description": "Sentenced Adults"
                      },
                      {
                        "key": "VULNERABLE_PRISONER_UNIT",
                        "description": "Vulnerable Prisoner Unit"
                      },
                      {
                        "key": "SPECIAL_UNIT",
                        "description": "Special Unit"
                      },
                      {
                        "key": "RESETTLEMENT_HOSTEL",
                        "description": "Resettlement Hostel"
                      },
                      {
                        "key": "HEALTHCARE_CENTRE",
                        "description": "Healthcare Centre"
                      },
                      {
                        "key": "NATIONAL_RESOURCE_HOSPITAL",
                        "description": "National Resource Hospital"
                      },
                      {
                        "key": "OTHER_SPECIFIED",
                        "description": "Other (Please Specify)"
                      },
                      {
                        "key": "REMAND_CENTRE",
                        "description": "Remand Centre"
                      },
                      {
                        "key": "LOCAL_PRISON",
                        "description": "Local Prison"
                      },
                      {
                        "key": "CLOSED_PRISON",
                        "description": "Closed Prison"
                      },
                      {
                        "key": "OPEN_TRAINING",
                        "description": "Open Training"
                      },
                      {
                        "key": "HOSTEL",
                        "description": "Hostel"
                      },
                      {
                        "key": "CLOSED_YOUNG_OFFENDER",
                        "description": "Closed Young Offender Institute"
                      },
                      {
                        "key": "OPEN_YOUNG_OFFENDER",
                        "description": "Open Young Offender Institute"
                      },
                      {
                        "key": "REMAND_UNDER_18",
                        "description": "Remand Institute for Under 18s"
                      },
                      {
                        "key": "SENTENCED_UNDER_18",
                        "description": "Institution for Sentenced Under 18s"
                      },
                      {
                        "key": "ECL_COMPONENT",
                        "description": "ECL Component Code"
                      },
                      {
                        "key": "ADDITIONAL_SPECIAL_UNIT",
                        "description": "Additional Special Unit"
                      },
                      {
                        "key": "SECOND_CLOSED_TRAINER",
                        "description": "Second Closed Trainer/Single LIDS Site"
                      },
                      {
                        "key": "IMMIGRATION_DETAINEES",
                        "description": "Immigration Detainees"
                      }
                    ]
                  },
                  {
                    "key": "LOCATION_ATTRIBUTE",
                    "description": "Location attribute",
                    "values": [
                      {
                        "key": "CAT_A_CELL",
                        "description": "Cat A Cell"
                      },
                      {
                        "key": "DOUBLE_OCCUPANCY",
                        "description": "Double Occupancy"
                      },
                      {
                        "key": "E_LIST_CELL",
                        "description": "E List Cell"
                      },
                      {
                        "key": "GATED_CELL",
                        "description": "Gated Cell"
                      },
                      {
                        "key": "LISTENER_CELL",
                        "description": "Listener Cell"
                      },
                      {
                        "key": "LOCATE_FLAT",
                        "description": "Locate Flat"
                      },
                      {
                        "key": "MULTIPLE_OCCUPANCY",
                        "description": "Multiple Occupancy"
                      },
                      {
                        "key": "NON_SMOKER_CELL",
                        "description": "Non Smoker Cell"
                      },
                      {
                        "key": "OBSERVATION_CELL",
                        "description": "Observation Cell"
                      },
                      {
                        "key": "SAFE_CELL",
                        "description": "Safe Cell"
                      },
                      {
                        "key": "SINGLE_OCCUPANCY",
                        "description": "Single Occupancy"
                      },
                      {
                        "key": "SPECIAL_CELL",
                        "description": "Special Cell"
                      },
                      {
                        "key": "WHEELCHAIR_ACCESS",
                        "description": "Wheelchair Access"
                      }
                    ]
                  },
                  {
                    "key": "SANITATION_FITTINGS",
                    "description": "Sanitation and fittings",
                    "values": [
                      {
                        "key": "ANTI_BARRICADE_DOOR",
                        "description": "Anti Barricade Door"
                      },
                      {
                        "key": "AUDITABLE_CELL_BELL",
                        "description": "Auditable Cell Bell"
                      },
                      {
                        "key": "FIXED_BED",
                        "description": "Fixed Bed"
                      },
                      {
                        "key": "METAL_DOOR",
                        "description": "Metal Door"
                      },
                      {
                        "key": "MOVABLE_BED",
                        "description": "Movable Bed"
                      },
                      {
                        "key": "PRIVACY_CURTAIN",
                        "description": "Privacy Curtain"
                      },
                      {
                        "key": "PRIVACY_SCREEN",
                        "description": "Privacy Screen"
                      },
                      {
                        "key": "STANDARD_CELL_BELL",
                        "description": "Standard Cell Bell"
                      },
                      {
                        "key": "SEPARATE_TOILET",
                        "description": "Separate Toilet"
                      },
                      {
                        "key": "WOODEN_DOOR",
                        "description": "Wooden Door"
                      }
                    ]
                  },
                  {
                    "key": "NON_ASSOCIATIONS",
                    "description": "Non-associations",
                    "values": [
                      {
                        "key": "CELL",
                        "description": "Do Not Locate in Same Cell"
                      },
                      {
                        "key": "LANDING",
                        "description": "Do Not Locate on Same Landing"
                      },
                      {
                        "key": "WING",
                        "description": "Do Not Locate on Same Wing"
                      }
                    ]
                  },
                  {
                    "key": "SECURITY",
                    "description": "Supervision and Security",
                    "values": [
                      {
                        "key": "CAT_A",
                        "description": "Cat A"
                      },
                      {
                        "key": "CAT_A_EX",
                        "description": "Cat A Ex"
                      },
                      {
                        "key": "CAT_A_HI",
                        "description": "Cat A Hi"
                      },
                      {
                        "key": "CAT_B",
                        "description": "Cat B"
                      },
                      {
                        "key": "CAT_C",
                        "description": "Cat C"
                      },
                      {
                        "key": "CAT_D",
                        "description": "Cat D"
                      },
                      {
                        "key": "ELIGIBLE",
                        "description": "Eligible"
                      },
                      {
                        "key": "PAROLE_GRANTED",
                        "description": "Parole Granted"
                      },
                      {
                        "key": "INELIGIBLE",
                        "description": "Ineligible"
                      },
                      {
                        "key": "YOI_CLOSED",
                        "description": "YOI Closed"
                      },
                      {
                        "key": "YOI_OPEN",
                        "description": "YOI Open"
                      },
                      {
                        "key": "YOI_RESTRICTED",
                        "description": "YOI Restricted"
                      },
                      {
                        "key": "YOI_SHORT_SENTENCE",
                        "description": "YOI Short Sentence"
                      },
                      {
                        "key": "YOI_LONG_TERM_CLOSED",
                        "description": "YOI Long-Term Closed"
                      },
                      {
                        "key": "UNCLASSIFIED",
                        "description": "Un-classified"
                      },
                      {
                        "key": "UNCATEGORISED_SENTENCED_MALE",
                        "description": "Un-categorised Sentenced Male"
                      },
                      {
                        "key": "LOW",
                        "description": "Low"
                      },
                      {
                        "key": "MEDIUM",
                        "description": "Medium"
                      },
                      {
                        "key": "HIGH",
                        "description": "High"
                      },
                      {
                        "key": "NOT_APPLICABLE",
                        "description": "Not Applicable"
                      },
                      {
                        "key": "PROV_A",
                        "description": "Prov A"
                      },
                      {
                        "key": "PENDING",
                        "description": "Pending"
                      },
                      {
                        "key": "REF_REVIEW",
                        "description": "Ref / Review - Date set for Review"
                      },
                      {
                        "key": "REFUSED_NO_REVIEW",
                        "description": "Refused - No Review possible"
                      },
                      {
                        "key": "STANDARD",
                        "description": "Standard"
                      },
                      {
                        "key": "FEMALE_RESTRICTED",
                        "description": "Female Restricted"
                      },
                      {
                        "key": "FEMALE_CLOSED",
                        "description": "Female Closed"
                      },
                      {
                        "key": "FEMALE_SEMI",
                        "description": "Female Semi"
                      },
                      {
                        "key": "FEMALE_OPEN",
                        "description": "Female Open"
                      },
                      {
                        "key": "UN_SENTENCED",
                        "description": "Un-sentenced"
                      },
                      {
                        "key": "YES",
                        "description": "Yes"
                      },
                      {
                        "key": "NO",
                        "description": "No"
                      }
                    ]
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /constants/service-types")
  @Nested
  inner class ViewServiceTypesConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/service-types")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/service-types")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/service-types")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve residential-attribute-type constants`() {
        webTestClient.get().uri("/constants/service-types")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "nonResidentialServiceTypes": [
                {
                  "key": "APPOINTMENT",
                  "description": "Appointments",
                  "attributes": {
                    "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                    "serviceFamilyDescription": "Activities and appointments"
                  },
                  "additionalInformation": "For example a counselling session"
                },
                {
                  "key": "PROGRAMMES_AND_ACTIVITIES",
                  "description": "Programmes and activities",
                  "attributes": {
                    "serviceFamilyType": "ACTIVITIES_APPOINTMENTS",
                    "serviceFamilyDescription": "Activities and appointments"
                  },
                  "additionalInformation": "For example a workshop or lesson"
                },
                {
                  "key": "HEARING_LOCATION",
                  "description": "Hearing location",
                  "attributes": {
                    "serviceFamilyType": "ADJUDICATIONS",
                    "serviceFamilyDescription": "Adjudications"
                  },
                  "additionalInformation": "For adjudication hearings"
                },
                {
                  "key": "LOCATION_OF_INCIDENT",
                  "description": "Location of incident",
                  "attributes": {
                    "serviceFamilyType": "ADJUDICATIONS",
                    "serviceFamilyDescription": "Adjudications"
                  },
                  "additionalInformation": "For example a location where an occurrence led to an adjudication hearing"
                },
                {
                  "key": "INTERNAL_MOVEMENTS",
                  "description": "Internal movements",
                  "attributes": {
                    "serviceFamilyType": "INTERNAL_MOVEMENTS",
                    "serviceFamilyDescription": "Internal movements"
                  },
                  "additionalInformation": "To record the location of unlocked prisoners within this establishment"
                },
                {
                  "key": "OFFICIAL_VISITS",
                  "description": "Official visits",
                  "attributes": {
                    "serviceFamilyType": "OFFICIAL_VISITS",
                    "serviceFamilyDescription": "Official visits"
                  },
                  "additionalInformation": "For example, arranging a face to face visit with a solicitor"
                },
                {
                  "key": "USE_OF_FORCE",
                  "description": "Use of force",
                  "attributes": {
                    "serviceFamilyType": "USE_OF_FORCE",
                    "serviceFamilyDescription": "Use of force"
                  },
                  "additionalInformation": "To report where a use of force incident took place"
                }
              ]
            }              
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /constants/service-family-types")
  @Nested
  inner class ViewServiceFamilyTypesConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/service-family-types")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/service-family-types")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/service-family-types")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve residential-attribute-type constants`() {
        webTestClient.get().uri("/constants/service-family-types")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "serviceFamilyTypes": [
                {
                  "key": "ACTIVITIES_APPOINTMENTS",
                  "description": "Activities and appointments",
                  "values": [
                    {
                      "key": "APPOINTMENT",
                      "description": "Appointments"
                    },
                    {
                      "key": "PROGRAMMES_AND_ACTIVITIES",
                      "description": "Programmes and activities"
                    }
                  ]
                },
                {
                  "key": "ADJUDICATIONS",
                  "description": "Adjudications",
                  "values": [
                    {
                      "key": "HEARING_LOCATION",
                      "description": "Hearing location"
                    },
                    {
                      "key": "LOCATION_OF_INCIDENT",
                      "description": "Location of incident"
                    }
                  ]
                },
                {
                  "key": "INTERNAL_MOVEMENTS",
                  "description": "Internal movements",
                  "values": [
                    {
                      "key": "INTERNAL_MOVEMENTS",
                      "description": "Internal movements"
                    }
                  ]
                },
                {
                  "key": "OFFICIAL_VISITS",
                  "description": "Official visits",
                  "values": [
                    {
                      "key": "OFFICIAL_VISITS",
                      "description": "Official visits"
                    }
                  ]
                },
                {
                  "key": "USE_OF_FORCE",
                  "description": "Use of force",
                  "values": [
                    {
                      "key": "USE_OF_FORCE",
                      "description": "Use of force"
                    }
                  ]
                }
              ]
            }              
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }

  @DisplayName("GET /constants/accommodation-type")
  @Nested
  inner class ViewAccommodationTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/accommodation-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/accommodation-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/accommodation-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve accommodation-type constants`() {
        webTestClient.get().uri("/constants/accommodation-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
                "accommodationTypes": [
                  {
                    "key": "CARE_AND_SEPARATION",
                    "description": "Care and separation"
                  },
                  {
                    "key": "HEALTHCARE_INPATIENTS",
                    "description": "Healthcare inpatients"
                  },
                  {
                    "key": "NORMAL_ACCOMMODATION",
                    "description": "Normal accommodation"
                  },
                  {
                    "key": "OTHER_NON_RESIDENTIAL",
                    "description": "Other"
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/specialist-cell-type")
  @Nested
  inner class ViewSpecialistCellTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/specialist-cell-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/specialist-cell-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/specialist-cell-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve specialist-cell-type constants`() {
        webTestClient.get().uri("/constants/specialist-cell-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
             {
              "specialistCellTypes": [
                {
                  "key": "ACCESSIBLE_CELL",
                  "description": "Accessible cell",
                  "attributes": {
                    "affectsCapacity": false
                  },
                  "additionalInformation": "Also known as wheelchair accessible or Disability and Discrimination Act (DDA) compliant"
                },
                {
                  "key": "BIOHAZARD_DIRTY_PROTEST",
                  "description": "Biohazard / dirty protest cell",
                  "attributes": {
                    "affectsCapacity": true
                  },
                  "additionalInformation": "Previously known as a dirty protest cell"
                },
                {
                  "key": "CSU",
                  "description": "Care and separation cell",
                  "attributes": {
                    "affectsCapacity": true
                  }
                },
                {
                  "key": "CAT_A",
                  "description": "Cat A cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "CONSTANT_SUPERVISION",
                  "description": "Constant supervision cell",
                  "attributes": {
                    "affectsCapacity": true
                  }
                },
                {
                  "key": "DRY",
                  "description": "Dry cell",
                  "attributes": {
                    "affectsCapacity": true
                  }
                },
                {
                  "key": "ESCAPE_LIST",
                  "description": "Escape list cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "ISOLATION_DISEASES",
                  "description": "Isolation cell for communicable diseases",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "LISTENER_CRISIS",
                  "description": "Listener / crisis cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "LOCATE_FLAT_CELL",
                  "description": "Locate flat",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "MEDICAL",
                  "description": "Medical cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "MOTHER_AND_BABY",
                  "description": "Mother and baby cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "SAFE_CELL",
                  "description": "Safe cell",
                  "attributes": {
                    "affectsCapacity": false
                  }
                },
                {
                  "key": "UNFURNISHED",
                  "description": "Unfurnished cell",
                  "attributes": {
                    "affectsCapacity": true
                  }
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/used-for-type")
  @Nested
  inner class ViewUsedForTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/used-for-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/used-for-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve used-for-type standard prison constants`() {
        webTestClient.get().uri("/constants/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "usedForTypes": [
                {
                  "key": "CLOSE_SUPERVISION_CENTRE",
                  "description": "Close Supervision Centre (CSC)"
                },
                {
                  "key": "SUB_MISUSE_DRUG_RECOVERY",
                  "description": "Drug recovery / Incentivised substance free living (ISFL)"
                },
                {
                  "key": "FIRST_NIGHT_CENTRE",
                  "description": "First night centre / Induction"
                },
                {
                  "key": "HIGH_SECURITY",
                  "description": "High security unit"
                },
                {
                  "key": "IPP_LONG_TERM_SENTENCES",
                  "description": "Long-term sentences / Imprisonment for public protection (IPP)"
                },
                {
                  "key": "MOTHER_AND_BABY",
                  "description": "Mother and baby"
                },
                {
                  "key": "OPEN_UNIT",
                  "description": "Open unit in a closed establishment"
                },
                {
                  "key": "PATHWAY_TO_PROG",
                  "description": "Pathway to progression"
                },
                {
                  "key": "PERINATAL_UNIT",
                  "description": "Perinatal unit"
                },
                {
                  "key": "PERSONALITY_DISORDER",
                  "description": "Personality disorder unit"
                },
                {
                  "key": "PIPE",
                  "description": "Psychologically informed planned environment (PIPE)"
                },
                {
                  "key": "REMAND",
                  "description": "Remand"
                },
                {
                  "key": "SEPARATION_CENTRE",
                  "description": "Separation centre"
                },
                {
                  "key": "STANDARD_ACCOMMODATION",
                  "description": "Standard accommodation"
                },
                {
                  "key": "THERAPEUTIC_COMMUNITY",
                  "description": "Therapeutic community"
                },
                {
                  "key": "VULNERABLE_PRISONERS",
                  "description": "Vulnerable prisoners"
                },
                {
                  "key": "YOUNG_PERSONS",
                  "description": "Young persons"
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/used-for-type/{prisonId}")
  @Nested
  inner class ViewUsedForTypeByPrisonConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/used-for-type/MDI")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/used-for-type/MDI")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/used-for-type/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `cannot retrieve used-for-type when prison Id in invalid format`() {
        webTestClient.get().uri("/constants/used-for-type/YYYYYYYYYY")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().is4xxClientError
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can retrieve used-for-type standard prison constants`() {
        prisonRegisterMockServer.stubLookupPrison("MDI")

        webTestClient.get().uri("/constants/used-for-type/MDI")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "usedForTypes": [
                  {
                    "key": "SUB_MISUSE_DRUG_RECOVERY",
                    "description": "Drug recovery / Incentivised substance free living (ISFL)"
                  },
                  {
                    "key": "FIRST_NIGHT_CENTRE",
                    "description": "First night centre / Induction"
                  },  
                  {
                    "key": "IPP_LONG_TERM_SENTENCES",
                    "description": "Long-term sentences / Imprisonment for public protection (IPP)"
                  },
                  {
                    "key": "OPEN_UNIT",
                    "description": "Open unit in a closed establishment"
                  },
                  {
                    "key": "PERSONALITY_DISORDER",
                    "description": "Personality disorder unit"
                  },
                  {
                    "key": "PIPE",
                    "description": "Psychologically informed planned environment (PIPE)"
                  },
                  {
                    "key": "REMAND",
                    "description": "Remand"
                  }, 
                  {
                    "key": "STANDARD_ACCOMMODATION",
                    "description": "Standard accommodation"
                  },
                  {
                    "key": "THERAPEUTIC_COMMUNITY",
                    "description": "Therapeutic community"
                  },
                  {
                    "key": "VULNERABLE_PRISONERS",
                    "description": "Vulnerable prisoners"
                  },
                  {
                    "key": "YOUNG_PERSONS",
                    "description": "Young persons"
                  }    
                ]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `can retrieve used-for-type female prison constants`() {
        prisonRegisterMockServer.stubLookupPrison("STI", female = true)

        webTestClient.get().uri("/constants/used-for-type/STI")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "usedForTypes": [
                {
                  "key": "SUB_MISUSE_DRUG_RECOVERY",
                  "description": "Drug recovery / Incentivised substance free living (ISFL)"
                },
                {
                  "key": "FIRST_NIGHT_CENTRE",
                  "description": "First night centre / Induction"
                },
                {
                  "key": "IPP_LONG_TERM_SENTENCES",
                  "description": "Long-term sentences / Imprisonment for public protection (IPP)"
                },
                {
                  "key": "MOTHER_AND_BABY",
                  "description": "Mother and baby"
                },
                {
                  "key": "OPEN_UNIT",
                  "description": "Open unit in a closed establishment"
                },
                {
                  "key": "PERINATAL_UNIT",
                  "description": "Perinatal unit"
                },
                {
                  "key": "PERSONALITY_DISORDER",
                  "description": "Personality disorder unit"
                },
                {
                  "key": "PIPE",
                  "description": "Psychologically informed planned environment (PIPE)"
                },
                {
                  "key": "REMAND",
                  "description": "Remand"
                },
                {
                  "key": "STANDARD_ACCOMMODATION",
                  "description": "Standard accommodation"
                },
                {
                  "key": "THERAPEUTIC_COMMUNITY",
                  "description": "Therapeutic community"
                },
                {
                  "key": "VULNERABLE_PRISONERS",
                  "description": "Vulnerable prisoners"
                },
                {
                  "key": "YOUNG_PERSONS",
                  "description": "Young persons"
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `can retrieve used-for-type secure estate prison constants`() {
        prisonRegisterMockServer.stubLookupPrison("WAI", lthse = true)

        webTestClient.get().uri("/constants/used-for-type/WAI")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
              "usedForTypes": [
                {
                  "key": "CLOSE_SUPERVISION_CENTRE",
                  "description": "Close Supervision Centre (CSC)"
                },
                {
                  "key": "SUB_MISUSE_DRUG_RECOVERY",
                  "description": "Drug recovery / Incentivised substance free living (ISFL)"
                },
                {
                  "key": "FIRST_NIGHT_CENTRE",
                  "description": "First night centre / Induction"
                },
                {
                  "key": "HIGH_SECURITY",
                  "description": "High security unit"
                },
                {
                  "key": "IPP_LONG_TERM_SENTENCES",
                  "description": "Long-term sentences / Imprisonment for public protection (IPP)"
                },
                {
                  "key": "OPEN_UNIT",
                  "description": "Open unit in a closed establishment"
                },
                {
                  "key": "PATHWAY_TO_PROG",
                  "description": "Pathway to progression"
                },
                {
                  "key": "PERSONALITY_DISORDER",
                  "description": "Personality disorder unit"
                },
                {
                  "key": "PIPE",
                  "description": "Psychologically informed planned environment (PIPE)"
                },
                {
                  "key": "REMAND",
                  "description": "Remand"
                },
                {
                  "key": "SEPARATION_CENTRE",
                  "description": "Separation centre"
                },
                {
                  "key": "STANDARD_ACCOMMODATION",
                  "description": "Standard accommodation"
                },
                {
                  "key": "THERAPEUTIC_COMMUNITY",
                  "description": "Therapeutic community"
                },
                {
                  "key": "VULNERABLE_PRISONERS",
                  "description": "Vulnerable prisoners"
                },
                {
                  "key": "YOUNG_PERSONS",
                  "description": "Young persons"
                }
              ]
            }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }

      @Test
      fun `cam retrieve used-for-type when prison does not exist in register`() {
        prisonRegisterMockServer.stubLookupPrison("ZZGHI", returnResult = false)

        webTestClient.get().uri("/constants/used-for-type/ZZGHI")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "usedForTypes": [
                  {
                    "key": "SUB_MISUSE_DRUG_RECOVERY",
                    "description": "Drug recovery / Incentivised substance free living (ISFL)"
                  },
                  {
                    "key": "FIRST_NIGHT_CENTRE",
                    "description": "First night centre / Induction"
                  },  
                  {
                    "key": "IPP_LONG_TERM_SENTENCES",
                    "description": "Long-term sentences / Imprisonment for public protection (IPP)"
                  },
                  {
                    "key": "OPEN_UNIT",
                    "description": "Open unit in a closed establishment"
                  },
                  {
                    "key": "PERSONALITY_DISORDER",
                    "description": "Personality disorder unit"
                  },
                  {
                    "key": "PIPE",
                    "description": "Psychologically informed planned environment (PIPE)"
                  },
                  {
                    "key": "REMAND",
                    "description": "Remand"
                  }, 
                  {
                    "key": "STANDARD_ACCOMMODATION",
                    "description": "Standard accommodation"
                  },
                  {
                    "key": "THERAPEUTIC_COMMUNITY",
                    "description": "Therapeutic community"
                  },
                  {
                    "key": "VULNERABLE_PRISONERS",
                    "description": "Vulnerable prisoners"
                  },
                  {
                    "key": "YOUNG_PERSONS",
                    "description": "Young persons"
                  }    
                ]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("GET /constants/converted-cell-type")
  @Nested
  inner class ViewConvertedCellTypeConstantsTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/constants/converted-cell-type")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/constants/converted-cell-type")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/constants/converted-cell-type")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve converted-cell-type constants`() {
        webTestClient.get().uri("/constants/converted-cell-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
                "convertedCellTypes": [
                  {
                    "key": "OFFICE",
                    "description": "Office"
                  },
                  {
                    "key": "SHOWER",
                    "description": "Shower"
                  },
                  {
                    "key": "STORE",
                    "description": "Store room"
                  },
                  {
                    "key": "UTILITY_ROOM",
                    "description": "Utility room"
                  },
                  {
                    "key": "HOLDING_ROOM",
                    "description": "Holding room"
                  },
                  {
                    "key": "INTERVIEW_ROOM",
                    "description": "Interview room"
                  },
                  {
                    "key": "KITCHEN_SERVERY",
                    "description": "Kitchen / Servery"
                  },
                  {
                    "key": "LISTENERS_ROOM",
                    "description": "Listener's room"
                  },
                  {
                    "key": "TREATMENT_ROOM",
                    "description": "Treatment room"
                  },
                  {
                    "key": "STAFF_ROOM",
                    "description": "Staff room"
                  },
                  {
                    "key": "OTHER",
                    "description": "Other"
                  }
                ]
              }
            """.trimIndent(),
            JsonCompareMode.LENIENT,
          )
      }
    }
  }
}
