package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.jdbc.core.ColumnMapRowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase

@DisplayName("Compare the result of migrations to in-built constant enumerations")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ConstantsTableTest : TestBase() {
  @Autowired
  lateinit var jdbcClient: JdbcClient

  private fun listAllConstants(query: String) = jdbcClient
    .sql("$query ORDER BY sequence")
    .query(ColumnMapRowMapper())
    .list()

  @Test
  fun `transaction type table`() {
    val expected = TransactionType.entries.map {
      mapOf(
        "code" to it.name,
        "description" to it.description,
      )
    }
    val actual = listAllConstants(
      // language=postgresql
      """
      SELECT code, description FROM constant_transaction_type
      """,
    )
    assertThat(actual).isEqualTo(expected)
  }
}
