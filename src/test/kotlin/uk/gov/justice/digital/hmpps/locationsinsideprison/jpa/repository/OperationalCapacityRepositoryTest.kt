package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.OperationalCapacity
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class OperationalCapacityRepositoryTest : TestBase() {

  @Autowired
  lateinit var repository: OperationalCapacityRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  @Test
  fun `Return null when capacity not defined for prison id`() {
    var oc = repository.findOneByPrisonId("MDI")
    assertThat(oc).isNull()
  }

  @Test
  fun `Return result when capacity defined for prison id`() {
    var operationalCapacity = OperationalCapacity(null, 100, "MDI", LocalDateTime.of(2024, 11, 11, 11, 11), "USER")
    repository.save(operationalCapacity)
    var oc = repository.findOneByPrisonId("MDI")
    assertThat(oc?.id).isNotNull()
    assertThat(oc?.capacity).isEqualTo(100)
    assertThat(oc?.prisonId).isEqualTo("MDI")
    assertThat(oc?.dateTime).isEqualTo(LocalDateTime.of(2024, 11, 11, 11, 11))
    assertThat(oc?.approvedBy).isEqualTo("USER")
  }
}
