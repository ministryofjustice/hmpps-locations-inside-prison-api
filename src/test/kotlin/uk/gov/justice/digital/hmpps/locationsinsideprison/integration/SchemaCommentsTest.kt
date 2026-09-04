package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Guards the data dictionary published to GitHub Pages (see
 * src/main/resources/db/migration/V1_104__schema_comments.sql).
 *
 * Descriptions live in the database as COMMENT ON statements so SchemaSpy, the CSV export and any
 * Glue crawl share one source of truth. Nothing else would notice a new column arriving undocumented,
 * and this schema is large enough - 20 tables and over 200 columns - that undocumented columns would
 * accumulate silently.
 *
 * The relkind filter includes 'v' as well as 'r'. There are no views today, but one added later would
 * otherwise slip into the published report undocumented.
 *
 * Extends [SqsIntegrationTestBase] rather than [IntegrationTestBase]: application-test.yml sets
 * hmpps.sqs.provider to localstack, so the context always wires HmppsQueueService.
 */
class SchemaCommentsTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `every table and view has a description`() {
    val undocumented = jdbcTemplate.queryForList(
      """
      SELECT c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relkind IN ('r', 'v')
        AND c.relname <> 'flyway_schema_history'
        AND obj_description(c.oid) IS NULL
      ORDER BY c.relname
      """.trimIndent(),
      String::class.java,
    )

    assertThat(undocumented)
      .describedAs("tables or views with no COMMENT ON - add one in a new migration")
      .isEmpty()
  }

  @Test
  fun `every column has a description`() {
    assertThat(columnComments().filter { it.comment == null }.map { it.name })
      .describedAs("columns with no COMMENT ON - add one in a new migration")
      .isEmpty()
  }

  @Test
  fun `every column description carries a sensitivity classification`() {
    val misclassified = columnComments()
      .filter { it.comment != null && !SENSITIVITY.containsMatchIn(it.comment) }
      .map { it.name }

    assertThat(misclassified)
      .describedAs("column comments must end with one of $SENSITIVITY - see V1_104__schema_comments.sql")
      .isEmpty()
  }

  private data class ColumnComment(
    val name: String,
    val comment: String?,
  )

  private fun columnComments(): List<ColumnComment> = jdbcTemplate.query(
    """
    SELECT c.relname || '.' || a.attname          AS name,
           col_description(c.oid, a.attnum)       AS comment
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'v')
      AND c.relname <> 'flyway_schema_history'
    ORDER BY c.relname, a.attnum
    """.trimIndent(),
  ) { rs, _ -> ColumnComment(rs.getString("name"), rs.getString("comment")) }

  private companion object {
    val SENSITIVITY = Regex("""\[Sensitivity: (NONE|PERSONAL|STAFF|SPECIAL-CATEGORY|OFFICIAL-SENSITIVE)]$""")
  }
}
