package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.json.JsonCompareMode
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonConfiguration
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonNotificationMailbox
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.PrisonNotificationMailboxRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import java.time.LocalDateTime

class PrisonNotificationMailboxResourceTest : SqsIntegrationTestBase() {

  @Autowired
  lateinit var prisonConfigurationRepository: PrisonConfigurationRepository

  @Autowired
  lateinit var prisonNotificationMailboxRepository: PrisonNotificationMailboxRepository

  val prisonId = "LEI"

  @BeforeEach
  fun setUp() {
    prisonConfigurationRepository.save(
      PrisonConfiguration(
        id = prisonId,
        whenUpdated = LocalDateTime.now(clock),
        updatedBy = "TEST",
      ),
    )
  }

  @AfterEach
  fun cleanUp() {
    prisonNotificationMailboxRepository.deleteAll()
  }

  @DisplayName("GET /prison-configuration/{prisonId}/notification-mailboxes/{notificationGroup}")
  @Nested
  inner class GetNotificationMailboxTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when invalid notification group used`() {
        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/XXXXX")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().is4xxClientError
      }

      @Test
      fun `not found when no mailbox exists for this prison and group`() {
        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can get notification mailbox`() {
        prisonNotificationMailboxRepository.save(
          PrisonNotificationMailbox(
            prisonId = prisonId,
            notificationGroup = NotificationGroup.CERT_ADMIN,
            emailAddress = "cert.admin@justice.gov.uk",
            whenUpdated = LocalDateTime.now(clock),
            updatedBy = "TEST",
          ),
        )

        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "notificationGroup": "CERT_ADMIN",
                "emailAddresses": ["cert.admin@justice.gov.uk"]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("PUT /prison-configuration/{prisonId}/notification-mailboxes/{notificationGroup}")
  @Nested
  inner class ReplaceNotificationMailboxTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .bodyValue(mapOf("emailAddresses" to listOf("cert.admin@justice.gov.uk")))
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf()))
          .bodyValue(mapOf("emailAddresses" to listOf("cert.admin@justice.gov.uk")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .bodyValue(mapOf("emailAddresses" to listOf("cert.admin@justice.gov.uk")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `error occurs when non existent prison used`() {
        webTestClient.put().uri("/prison-configuration/JJI/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .bodyValue(mapOf("emailAddresses" to listOf("cert.admin@justice.gov.uk")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `error occurs when email address is invalid`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .bodyValue(mapOf("emailAddresses" to listOf("not-an-email")))
          .exchange()
          .expectStatus().isBadRequest
      }

      @Test
      fun `error occurs when email list is empty`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .bodyValue(mapOf("emailAddresses" to emptyList<String>()))
          .exchange()
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can replace notification mailbox and overwrites existing addresses`() {
        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .bodyValue(mapOf("emailAddresses" to listOf("first@justice.gov.uk")))
          .exchange()
          .expectStatus().isOk

        webTestClient.put().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .bodyValue(mapOf("emailAddresses" to listOf("second@justice.gov.uk", "third@justice.gov.uk")))
          .exchange()
          .expectStatus().isOk
          .expectBody().json(
            """
              {
                "prisonId": "$prisonId",
                "notificationGroup": "CERT_ADMIN",
                "emailAddresses": ["second@justice.gov.uk", "third@justice.gov.uk"]
              }
            """.trimIndent(),
            JsonCompareMode.STRICT,
          )
      }
    }
  }

  @DisplayName("DELETE /prison-configuration/{prisonId}/notification-mailboxes/{notificationGroup}")
  @Nested
  inner class DeleteNotificationMailboxTest {

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no authority`() {
        webTestClient.delete().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class Validation {

      @Test
      fun `not found when no mailbox exists for this prison and group`() {
        webTestClient.delete().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {

      @Test
      fun `can delete notification mailbox`() {
        prisonNotificationMailboxRepository.save(
          PrisonNotificationMailbox(
            prisonId = prisonId,
            notificationGroup = NotificationGroup.CERT_ADMIN,
            emailAddress = "cert.admin@justice.gov.uk",
            whenUpdated = LocalDateTime.now(clock),
            updatedBy = "TEST",
          ),
        )

        webTestClient.delete().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isNoContent

        webTestClient.get().uri("/prison-configuration/$prisonId/notification-mailboxes/CERT_ADMIN")
          .headers(setAuthorisation(roles = listOf("ROLE_LOCATION_CONFIG_ADMIN")))
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }
}
