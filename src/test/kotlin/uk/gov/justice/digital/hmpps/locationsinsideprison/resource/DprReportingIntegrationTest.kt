package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Value
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.CommonDataTestBase

private const val REQUESTING_USER = "request-user"

@DisplayName("DPR reporting resource tests")
class DprReportingIntegrationTest : CommonDataTestBase() {

  @Value("\${dpr.lib.system.role}")
  lateinit var systemRole: String

  @BeforeEach
  override fun setUp() {
    super.setUp()
    manageUsersApiMockServer.stubLookupUsersRoles(REQUESTING_USER, listOf("MANAGE_RES_LOCATIONS_OP_CAP"))
    manageUsersApiMockServer.stubLookupUserCaseload(REQUESTING_USER, "LEI", listOf("MDI"))
  }

  @DisplayName("GET /definitions")
  @Nested
  inner class GetDefinitions {
    private val url = "/definitions"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri(url),
        systemRole,
      )
    }

    @DisplayName("works")
    @Nested
    inner class HappyPath {

      @Test
      fun `returns the definitions of all the reports`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].authorised").isEqualTo("true")
      }

      @Test
      fun `returns the definitions of all the reports but not authorises as no user in context`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(user = null, roles = listOf(systemRole), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody().jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].authorised").isEqualTo("false")
      }

      @Test
      fun `returns the not auth definitions of the reports`() {
        manageUsersApiMockServer.stubLookupUsersRoles(REQUESTING_USER, listOf("ANOTHER_USER_ROLE"))

        webTestClient.get().uri(url)
          .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.length()").isEqualTo(1)
          .jsonPath("$[0].authorised").isEqualTo("false")
      }
    }
  }

  @DisplayName("GET /definitions/transactions/residential")
  @Nested
  inner class GetDefinitionDetails {
    private val url = "/definitions/transactions/residential"

    @DisplayName("is secured")
    @Nested
    inner class Security {
      @DisplayName("by role and scope")
      @TestFactory
      fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
        webTestClient.get().uri(url),
        systemRole,
      )
    }

    @Test
    fun `report definition denied when user has incorrect role`() {
      manageUsersApiMockServer.stubLookupUsersRoles(REQUESTING_USER, listOf("ANOTHER_USER_ROLE"))

      webTestClient.get().uri(url)
        .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @DisplayName("works")
    @Nested
    inner class HappyPath {

      @Test
      fun `returns the definition of the report`() {
        webTestClient.get().uri(url)
          .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .json(
            // language=json
            """
           {
              "id": "transactions",
              "name": "Location transaction reports",
              "description": "Reports detailing transactions made to internal locations",
              "variant": {
                "id": "residential",
                "name": "Residential location transactions",
                "resourceName": "reports/transactions/residential",
                "description": "Details each transaction that has occurred in a residential location",
                "printable": true
              }
            }
            """,
          )
      }
    }
  }

  @DisplayName("GET /reports")
  @Nested
  inner class GetReports {
    @DisplayName("GET /reports/transactions/residential")
    @Nested
    inner class RunTransactionReport {
      private val url = "/reports/transactions/residential"

      @DisplayName("is secured")
      @Nested
      inner class Security {
        @DisplayName("by role and scope")
        @TestFactory
        fun endpointRequiresAuthorisation() = endpointRequiresAuthorisation(
          webTestClient.get().uri(url),
          systemRole,
        )
      }

      @Test
      fun `returns 403 when user does not have the role`() {
        manageUsersApiMockServer.stubLookupUsersRoles(REQUESTING_USER, listOf("ANOTHER_USER_ROLE"))

        webTestClient.get().uri(url)
          .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @DisplayName("works")
      @Nested
      inner class HappyPath {

        @Test
        fun `returns a page of the report`() {
          webTestClient.get().uri("$url?filters.service_active=Y&filters.transaction_type=LOCATION_CREATE")
            .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(10)
        }

        @Test
        fun `returns no data when user does not have the caseload`() {
          manageUsersApiMockServer.stubLookupUserCaseload(REQUESTING_USER, "BXI", listOf("BXI"))

          webTestClient.get().uri("$url?filters.service_active=Y&filters.transaction_type=LOCATION_CREATE")
            .headers(setAuthorisation(user = REQUESTING_USER, roles = listOf(systemRole), scopes = listOf("read")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)
        }
      }
    }
  }
}
