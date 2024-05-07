package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

class LocationConstantsIntTest : SqsIntegrationTestBase() {

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
                    "key": "REFURBISHMENT",
                    "description": "Refurbishment"
                  },
                  {
                    "key": "MAINTENANCE",
                    "description": "Maintenance"
                  },
                  {
                    "key": "STAFF_SHORTAGE",
                    "description": "Staff shortage"
                  },
                  {
                    "key": "MOTHBALLED",
                    "description": "Mothballed"
                  },
                  {
                    "key": "DAMAGED",
                    "description": "Damage"
                  },
                  {
                    "key": "DAMP",
                    "description": "Dump / mould"
                  },
                  {
                    "key": "PEST",
                    "description": "Pest control"
                  },
                  {
                    "key": "SECURITY_SEALED",
                    "description": "Security sealed"
                  },
                  {
                    "key": "OTHER",
                    "description": "Other"
                  }
                ]
              }
            """.trimIndent(),
            false,
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
                    "key": "NORMAL_ACCOMMODATION",
                    "description": "Normal Accommodation"
                  },
                  {
                    "key": "HEALTHCARE",
                    "description": "Healthcare"
                  },
                  {
                    "key": "HOLDING_CELL",
                    "description": "Holding Cell"
                  },
                  {
                    "key": "OTHER_USE",
                    "description": "Other Use"
                  },
                  {
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
                  }
                ]
              }
            """.trimIndent(),
            false,
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
               "key": "APPOINTMENT",
               "description": "Appointment"
             },
             {
               "key": "VISIT",
               "description": "Visit"
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
               "key": "ADJUDICATION_HEARING",
               "description": "Adjudication hearing"
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
               "key": "OTHER",
               "description": "Other"
             }
           ]
         }
            """.trimIndent(),
            false,
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
            false,
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
                    "key": "NORMAL_ACCOMMODATION",
                    "description": "Normal Accommodation"
                  },
                  {
                    "key": "HEALTHCARE_INPATIENTS",
                    "description": "Health Care In-patients"
                  },
                  {
                    "key": "CARE_AND_SEPARATION",
                    "description": "Care and Separation"
                  },
                  {
                    "key": "OTHER_NON_RESIDENTIAL",
                    "description": "Other Non Residential"
                  }
                ]
              }
            """.trimIndent(),
            false,
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
                "accommodationTypes": [
                  {
                    "key": "BIOHAZARD_DIRTY_PROTEST",
                    "description": "Biohazard / dirty protest cell"
                  },
                  {
                    "key": "CAT_A",
                    "description": "Cat A cell"
                  },
                  {
                    "key": "CONSTANT_SUPERVISION",
                    "description": "Constant supervision cell"
                  },
                  {
                    "key": "CSU",
                    "description": "CSU cell"
                  },
                  {
                    "key": "DRY",
                    "description": "Dry cell"
                  },
                  {
                    "key": "ESCAPE_LIST",
                    "description": "Escape list cell"
                  },
                  {
                    "key": "FIRE_RESISTANT",
                    "description": "Fire resistant cell"
                  },
                  {
                    "key": "FIXES_FURNITURE",
                    "description": "Fixed furniture cell"
                  }, 
                  {
                    "key": "ISOLATION_DISEASES",
                    "description": "Isolation cell for communicable diseases"
                  }, 
                  {
                    "key": "LIGATURE_RESISTANT",
                    "description": "Ligature resistant cell"
                  }, 
                  {
                    "key": "LISTENER_CRISIS",
                    "description": "Listener / crisis cell"
                  },
                  {
                    "key": "LOW_MOBILITY",
                    "description": "Low mobility cell"
                  },
                  {
                    "key": "MEDICAL",
                    "description": "Medical cell"
                  },
                  {
                    "key": "MOTHER_AND_BABY",
                    "description": "Mother and baby cell"
                  },
                  {
                    "key": "SOUND_RESISTANT",
                    "description": "Sound resistant cell"
                  },
                  {
                    "key": "UNFURNISHED",
                    "description": "Unfurnished cell"
                  },
                  {
                    "key": "WHEELCHAIR_ACCESSIBLE",
                    "description": "Wheelchair accessible cells"
                  },
                ]
              }
            """.trimIndent(),
            false,
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
      fun `can retrieve used-for-type constants`() {
        webTestClient.get().uri("/constants/used-for-type")
          .headers(setAuthorisation(roles = listOf("ROLE_READ_LOCATION_REFERENCE_DATA")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
            {
                "usedForTypes": [
                  {
                    "key": "STANDARD_ACCOMMODATION",
                    "description": "Standard accommodation"
                  },
                  {
                    "key": "PERSONALITY_DISORDER,
                    "description": "Personality Disorder"
                  },
                  {
                    "key": "THERAPEUTIC_COMMUNITY,
                    "description": "Therapeutic Community"
                  },
                  {
                    "key": "PIPE,
                    "description": "PIPE"
                  },
                  {
                    "key": "SUB_MISUSE_DRUG_RECOVERY,
                    "description": "Substance Misuse / Drug Revovery / ISFL"
                  },
                  {
                    "key": "VULNERABLE_PRISONERS,
                    "description": "Vulnerable Prisoners"
                  },
                  {
                    "key": "FIRST_NIGHT_CENTRE,
                    "description": "First night centre / Induction"
                  },  
                  {
                    "key": "REMAND,
                    "description": "Remand"
                  }, 
                  {
                    "key": "MOTHER_AND_BABY,
                    "description": "Mother and Baby"
                  },   
                  {
                    "key": "YOUNG_PERSONS,
                    "description": "Young persons"
                  },    
                  {
                    "key": "HIGH_SECURITY,
                    "description": "High security"
                  }, 
                  {
                    "key": "CLOSE_SUPERVISION_CENTRE,
                    "description": "Close Supervision Centre (CSC)"
                  }, 
                  {
                    "key": "PATHWAY_TO_PROG,
                    "description": "Pathway To Progression"
                  },
                  {
                    "key": "IPP_LONG_TERM_SENTENCES,
                    "description": "IPP / Long Term Sentences"
                  }
                ]
              }
            """.trimIndent(),
            false,
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
                    "description": "Interview Room"
                  },
                  {
                    "key": "KITCHEN_SERVERY",
                    "description": "Kitchen / Servery"
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
            false,
          )
      }
    }
  }
}
