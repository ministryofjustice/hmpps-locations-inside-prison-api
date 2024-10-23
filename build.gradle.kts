import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.7"
  kotlin("plugin.spring") version "2.0.21"
  kotlin("plugin.jpa") version "2.0.21"
  id("org.jetbrains.kotlinx.kover") version "0.9.0-RC"
  idea
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.0.7")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.0.1")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.7.0")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  implementation("com.zaxxer:HikariCP:6.0.0")
  runtimeOnly("org.postgresql:postgresql")
  implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.0.7")
  testImplementation("org.wiremock:wiremock-standalone:3.9.2")

  testImplementation("com.pauldijou:jwt-core_2.11:5.0.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.23")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.testcontainers:localstack:1.20.3")
  testImplementation("org.testcontainers:postgresql:1.20.3")
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
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
