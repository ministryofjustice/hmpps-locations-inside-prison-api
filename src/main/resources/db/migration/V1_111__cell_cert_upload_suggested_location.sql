-- MAPA-403: a spreadsheet can drop the leading zeros from a cell number, so a row for "B-1-5" fails while the real
-- cell "B-1-005" is reported as not in the upload. Record the likely match on both, so the report shows one mistake
-- rather than two. A suggestion only: nothing is applied.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN suggested_location_key VARCHAR(255) NULL;

ALTER TABLE cell_certificate_upload_not_on_certificate
    ADD COLUMN uploaded_as_key VARCHAR(255) NULL;

COMMENT ON COLUMN cell_certificate_upload_location.suggested_location_key IS 'For a row whose location was not found: the cell it most likely meant, when the names differ only by dropped leading zeros. A suggestion only. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.uploaded_as_key IS 'The name a failed row most likely used for this cell, when the names differ only by dropped leading zeros. [Sensitivity: OFFICIAL-SENSITIVE]';
