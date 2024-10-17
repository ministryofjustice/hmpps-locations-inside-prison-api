package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.SqsIntegrationTestBase

@ActiveProfiles("test")
class ResetTrainingEnvironmentResourceTest : SqsIntegrationTestBase() {

  @DisplayName("PUT /reset-training")
  @Nested
  inner class ResetTrainingTest {

    @Nested
    inner class Security {
      @Test
      fun `in non training profile the reset training is not available`() {
        webTestClient.put().uri("/reset-training")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }
  }
}
