package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PrisonConfigurationRepositoryTest : TestBase() {

  val testPrisonId = "MDI"
  val testUser = "USER"

  @Autowired
  lateinit var repository: PrisonConfigurationRepository

  @AfterEach
  fun cleanUp() {
    repository.deleteAll()
  }

  @Test
  fun `Return null when capacity not defined for prison id`() {
    val oc = repository.findById(testPrisonId).getOrNull()
    assertThat(oc).isNull()
  }

  @Test
  @Sql("classpath:repository/insert-prison-signed-operation-capacity.sql")
  fun `Return result when capacity defined for prison id`() {
    val oc = repository.findById(testPrisonId).getOrNull()
    assertThat(oc?.signedOperationCapacity).isEqualTo(130)
    assertThat(oc?.prisonId).isEqualTo(testPrisonId)
    assertThat(oc?.whenUpdated).isEqualTo(LocalDateTime.now(clock))
    assertThat(oc?.updatedBy).isEqualTo(testUser)
    assertThat(oc.toString()).contains("PrisonConfiguration")
    assertThat(oc.toString()).contains(testPrisonId)
  }
}
