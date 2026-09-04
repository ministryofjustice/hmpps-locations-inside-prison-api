package uk.gov.justice.digital.hmpps.locationsinsideprison.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.AccommodationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ConvertedCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.DeactivatedReason
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationAttribute
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialAttributeValue
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ResidentialHousingType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SpecialistCellType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.UsedForType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.VirtualLocationCode
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalRequestStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.approvalrequest.ApprovalType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.cellcertupload.CellCertificateUploadStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import java.io.File

/**
 * Writes reference-data.csv, the permitted values behind every coded column in the schema.
 *
 * Almost every code in this schema is a Kotlin enum persisted as a varchar, with no check constraint
 * and no lookup table, so a consumer reading the schema alone sees a varchar(60) with no idea which
 * values are legal. The one exception is constant_transaction_type, which exists as a table precisely
 * so reporting tools can decode linked_transaction.transaction_type - it is exported from the database
 * rather than from the enum, so the CSV reflects what is actually there.
 *
 * Descriptions are read from each enum's own `description` property rather than being restated here, so
 * they cannot drift from the code. The three enums with no description carry one below; adding a value
 * to any of those three without describing it fails the build rather than exporting a blank row.
 *
 * Excluded from normal test runs; run with `./gradlew -Pinit-db=true test` (see build.gradle.kts).
 */
class ExportReferenceData : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `exports reference data`() {
    val rows = mutableListOf<Row>()

    rows += transactionTypeRows()

    rows += enumRows("location.location_type", LocationType.entries) { it.description }
    rows += enumRows("location.status", LocationStatus.entries) { it.description }
    rows += enumRows("location.accommodation_type", AccommodationType.entries) { it.description }
    rows += enumRows("location.residential_housing_type", ResidentialHousingType.entries, notes = "legacy NOMIS classification, superseded by accommodation_type") { it.description }
    rows += enumRows("location.deactivated_reason", DeactivatedReason.entries) { it.description }
    rows += enumRows("location.converted_cell_type", ConvertedCellType.entries) { it.description }
    rows += discriminatorRows()
    rows += enumRows("location.code", VirtualLocationCode.entries, notes = "codes used by virtual locations, where location_type_discriminator is VIRTUAL") { VIRTUAL_LOCATION_CODES.getValue(it) }

    rows += enumRows("specialist_cell.specialist_cell_type", SpecialistCellType.entries) { it.description }
    rows += enumRows("cell_used_for.used_for", UsedForType.entries) { it.description }
    rows += enumRows("service_usage.service_type", ServiceType.entries) { it.description }
    rows += enumRows("non_residential_usage.usage_type", NonResidentialUsageType.entries, notes = "legacy NOMIS classification, superseded by service_usage.service_type") { it.description }
    rows += enumRows("residential_attribute.attribute_type", ResidentialAttributeType.entries, notes = "legacy NOMIS attribute domain") { it.description }
    rows += enumRows("residential_attribute.attribute_value", ResidentialAttributeValue.entries, notes = "legacy NOMIS attribute value") { it.description }
    rows += enumRows("location_history.attribute_name", LocationAttribute.entries) { it.description }

    rows += enumRows("certification_approval_request.status", ApprovalRequestStatus.entries) { APPROVAL_REQUEST_STATUSES.getValue(it) }
    rows += enumRows("certification_approval_request.approval_type", ApprovalType.entries) { it.description }
    rows += enumRows("cell_certificate_upload.status", CellCertificateUploadStatus.entries) { it.description }
    rows += enumRows("cell_certificate_upload_location.status", CellCertificateUploadLocationStatus.entries) { it.description }
    rows += enumRows("prison_notification_mailbox.notification_group", NotificationGroup.entries) { NOTIFICATION_GROUPS.getValue(it) }

    assertThat(rows.filter { it.description.isBlank() }.map { "${it.columnRef}.${it.code}" })
      .describedAs("every exported value needs a description - an undescribed code is not reference data")
      .isEmpty()

    val output = File(System.getProperty("referenceDataOutput") ?: "reference-data.csv")
    output.bufferedWriter().use { writer ->
      writer.write("column_ref,code,description,notes\n")
      rows.forEach { writer.write("${it.toCsv()}\n") }
    }
    println("Wrote ${rows.size} reference data rows to ${output.absolutePath}")
  }

  /**
   * Exported from the table rather than from TransactionType, because the table is what a reporting tool
   * actually joins to. ConstantsTableTest already guards it against drifting from the enum.
   */
  private fun transactionTypeRows(): List<Row> = jdbcTemplate.query(
    """
    SELECT code, description
    FROM constant_transaction_type
    ORDER BY sequence, code
    """.trimIndent(),
  ) { rs, _ ->
    Row(
      columnRef = "linked_transaction.transaction_type",
      code = rs.getString("code"),
      description = rs.getString("description"),
      notes = "also held in the constant_transaction_type table",
    )
  }

  private fun discriminatorRows(): List<Row> = DISCRIMINATORS.map { (code, description) ->
    Row("location.location_type_discriminator", code, description, "single-table inheritance discriminator, not a Kotlin enum")
  }

  private fun <T : Enum<T>> enumRows(
    columnRef: String,
    values: List<T>,
    notes: String = "",
    describe: (T) -> String,
  ): List<Row> = values.map { Row(columnRef, it.name, describe(it), notes) }

  private data class Row(
    val columnRef: String,
    val code: String,
    val description: String,
    val notes: String = "",
  ) {
    fun toCsv() = listOf(columnRef, code, description, notes).joinToString(",") { escape(it) }

    private fun escape(value: String) = "\"${value.replace("\"", "\"\"")}\""
  }

  private companion object {
    val DISCRIMINATORS = listOf(
      "CELL" to "An individual cell that can hold prisoners",
      "RESIDENTIAL" to "A residential location above cell level, such as a wing or landing",
      "NON_RESIDENTIAL" to "A location that does not hold prisoners overnight, such as an adjudication room",
      "VIRTUAL" to "A location that does not physically exist, used to record prisoners who are not in a cell",
    )

    val VIRTUAL_LOCATION_CODES = mapOf(
      VirtualLocationCode.RECP to "Reception",
      VirtualLocationCode.COURT to "At court",
      VirtualLocationCode.TAP to "Released on temporary licence",
      VirtualLocationCode.CSWAP to "Awaiting a cell",
    )

    val APPROVAL_REQUEST_STATUSES = mapOf(
      ApprovalRequestStatus.PENDING to "Raised and awaiting a decision",
      ApprovalRequestStatus.APPROVED to "Approved; the change has been applied",
      ApprovalRequestStatus.REJECTED to "Rejected; the change was not applied",
      ApprovalRequestStatus.WITHDRAWN to "Withdrawn by the requester before a decision was made",
    )

    val NOTIFICATION_GROUPS = mapOf(
      NotificationGroup.CERT_ADMIN to "Notified of certification activity needing administration",
      NotificationGroup.CERT_VIEWER to "Notified of certification activity for information only",
      NotificationGroup.CERT_REVIEWER to "Notified when a certification approval request needs reviewing",
    )
  }
}
