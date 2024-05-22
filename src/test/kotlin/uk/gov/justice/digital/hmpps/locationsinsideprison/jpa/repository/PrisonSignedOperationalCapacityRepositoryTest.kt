package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.PrisonSignedOperationalCapacity
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class PrisonSignedOperationalCapacityRepositoryTest : TestBase() {

  @Autowired
  lateinit var repository: PrisonSignedOperationalCapacityRepository

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
    var operationalCapacity = PrisonSignedOperationalCapacity(null, 100, "MDI", LocalDateTime.now(clock), "USER")
    repository.save(operationalCapacity)
    var oc = repository.findOneByPrisonId("MDI")
    assertThat(oc?.id).isNotNull()
    assertThat(oc?.signedOperationCapacity).isEqualTo(100)
    assertThat(oc?.prisonId).isEqualTo("MDI")
    assertThat(oc?.dateTime).isEqualTo(LocalDateTime.now(clock))
    assertThat(oc?.approvedBy).isEqualTo("USER")
  }
}
