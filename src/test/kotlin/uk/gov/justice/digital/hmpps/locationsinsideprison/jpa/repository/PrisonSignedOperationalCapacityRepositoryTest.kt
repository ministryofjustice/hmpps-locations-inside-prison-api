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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PrisonSignedOperationalCapacityRepositoryTest : TestBase() {

  val mdi = "MDI"
  val xxx = "XXX"
  val testUser = "USER"

  @Autowired
  lateinit var repository: PrisonSignedOperationCapacityRepository

  @AfterEach
  fun cleanUp() {
    repository.deleteAll()
  }

  @Test
  fun `Return null when capacity not defined for prison id`() {
    val oc = repository.findOneByPrisonId(mdi)
    assertThat(oc).isNull()
  }

  @Test
  @Sql("classpath:repository/insert-prison-signed-operation-capacity.sql")
  fun `Return result when capacity defined for prison id`() {
    val oc = repository.findOneByPrisonId(mdi)
    assertThat(oc?.id).isNotNull()
    assertThat(oc?.signedOperationCapacity).isEqualTo(130)
    assertThat(oc?.prisonId).isEqualTo(mdi)
    assertThat(oc?.whenUpdated).isEqualTo(LocalDateTime.now(clock))
    assertThat(oc?.updatedBy).isEqualTo(testUser)
    assertThat(oc.toString()).contains("Prison Signed Operation Capacity")
    assertThat(oc.toString()).contains(mdi)
  }
}
