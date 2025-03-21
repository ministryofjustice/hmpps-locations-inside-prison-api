import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import uk.gov.justice.digital.hmpps.gradle.PortForwardRDSTask
import uk.gov.justice.digital.hmpps.gradle.PortForwardRedisTask
import uk.gov.justice.digital.hmpps.gradle.RevealSecretsTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.4"
  kotlin("plugin.spring") version "2.1.10"
  kotlin("plugin.jpa") version "2.1.10"
  id("org.jetbrains.kotlinx.kover") version "0.9.1"
  idea
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.4.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("uk.gov.justice.service.hmpps:hmpps-digital-prison-reporting-lib:8.0.1")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.2")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.13.3")

  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  implementation("com.zaxxer:HikariCP:6.2.1")
  runtimeOnly("org.postgresql:postgresql")
  implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.0")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")

  testImplementation("com.pauldijou:jwt-core_2.11:5.0.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.testcontainers:localstack:1.20.6")
  testImplementation("org.testcontainers:postgresql:1.20.6")
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
