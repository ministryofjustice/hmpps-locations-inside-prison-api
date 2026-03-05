# Project Guidelines

This project is a Kotlin Spring Boot API for managing locations inside a prison, based on the [HMPPS Kotlin Template](https://github.com/ministryofjustice/hmpps-template-kotlin).

## Build and Configuration

### Environment Requirements
- **Java/Kotlin**: The project uses **Java 25** and **Kotlin 25**.
- **Database**: PostgreSQL is used for data storage.
- **Messaging**: AWS SQS/SNS are used for event-driven architecture.

### Build Instructions
To build the project and run checks:
```bash
./gradlew build
```

### Running Locally
1. Start the required infrastructure (Postgres, LocalStack) using Docker Compose:
   ```bash
   docker compose up
   ```
2. Run the application:
   ```bash
   ./gradlew bootRun
   ```

To run against dev services, set the following environment variables:
- `API_BASE_URL_OAUTH=https://sign-in-dev.hmpps.service.justice.gov.uk/auth`
- `API_BASE_URL_PRISON=https://prison-api-dev.prison.service.justice.gov.uk`
- `LOCATIONS_INSIDE_PRISON_API_CLIENT_ID=[your client id]`
- `LOCATIONS_INSIDE_PRISON_API_CLIENT_SECRET=[your client secret]`

## Testing

### Running Tests
- **All tests**: `./gradlew test`
- **Coverage report**: `./gradlew koverHtmlReport` (output found in `build/reports/kover/html/index.html`)

### Adding New Tests
- **Unit Tests**: Place in `src/test/kotlin/...` following the package structure.
- **Integration Tests**: 
  - Should inherit from `IntegrationTestBase` for standard web-client testing with WireMock.
  - Should inherit from `SqsIntegrationTestBase` if the test requires SQS/SNS infrastructure (this starts LocalStack via Testcontainers).
  - Testcontainers (Postgres and LocalStack) are used automatically when running tests that inherit from these base classes.

### Example Integration Test
A simple test to verify the health ping endpoint:
```kotlin
package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.junit.jupiter.api.Test

class DemoTest : SqsIntegrationTestBase() {
  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }
}
```
*Note: Inheritance from `SqsIntegrationTestBase` is often required because the application context initializes SQS beans, which fail if LocalStack is not available.*

## Development Information

### Code Style
- The project uses **ktlint** for code formatting.
- Check style: `./gradlew ktlintCheck`
- Format code: `./gradlew ktlintFormat`

### Architecture and Dependencies
- **Flyway**: Used for database migrations (`src/main/resources/db/migration`).
- **WireMock**: Used for mocking downstream APIs in tests.
- **OpenAPI**: Documentation is available via Swagger UI when running locally.
- **AsyncAPI**: Event documentation is available in `async-api.yml`.
