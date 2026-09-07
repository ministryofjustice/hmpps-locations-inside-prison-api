package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.junit.jupiter.api.Test

/**
 * Builds the schema so the SchemaSpy report can be generated against it.
 *
 * Excluded from normal test runs; run with `./gradlew -Pinit-db=true test` (see build.gradle.kts).
 * Starting the application context is enough - Flyway migrates on startup.
 *
 * Extends [SqsIntegrationTestBase] rather than [IntegrationTestBase] even though it needs nothing from
 * SQS: application-test.yml sets hmpps.sqs.provider to localstack, so the context always wires
 * HmppsQueueService and cannot start without SNS/SQS on 4566.
 */
class InitialiseDatabase : SqsIntegrationTestBase() {

  @Test
  fun `initialises database`() {
    println("Database has been initialised by SqsIntegrationTestBase")
  }
}
