import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.6"
  kotlin("plugin.spring") version "1.9.23"
  kotlin("plugin.jpa") version "1.9.23"
  idea
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:0.2.4")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:3.1.3")
  implementation("io.opentelemetry:opentelemetry-api:1.37.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.3.0")

  implementation("org.flywaydb:flyway-core")
  implementation("com.zaxxer:HikariCP:5.1.0")
  runtimeOnly("org.postgresql:postgresql")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("org.wiremock:wiremock-standalone:3.5.4")

  testImplementation("com.pauldijou:jwt-core_2.11:5.0.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.1")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.5")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.22")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.testcontainers:localstack:1.19.7")
  testImplementation("org.testcontainers:postgresql:1.19.7")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  register<PortForwardRDSTask>("portForwardRDS") {
    namespacePrefix = "hmpps-locations-inside-prison"
  }

  register<PortForwardRedisTask>("portForwardRedis") {
    namespacePrefix = "hmpps-locations-inside-prison"
  }

  register<RevealSecretsTask>("revealSecrets") {
    namespacePrefix = "hmpps-locations-inside-prison"
  }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_21.toString()
    }
  }
}
