-- An import only certifies the cells it lists, but the certificate generated at the end covers
-- every certifiable cell in the prison, so any live cell missing from the upload is carried onto
-- the new certificate at its current values. Record which cells that happened to so the ingestion
-- report can disclose it, rather than silently changing what has been certified.

ALTER TABLE cell_certificate_upload
    ADD COLUMN not_on_certificate_records INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN cell_certificate_upload.not_on_certificate_records IS 'How many certifiable cells had no row in the upload and so were carried onto the new certificate at their current values. [Sensitivity: NONE]';

CREATE TABLE cell_certificate_upload_not_on_certificate
(
    cell_certificate_upload_id UUID         NOT NULL,
    location_key               VARCHAR(255) NOT NULL,
    CONSTRAINT fk_cell_certificate_upload_not_on_certificate_upload
        FOREIGN KEY (cell_certificate_upload_id) REFERENCES cell_certificate_upload (id) ON DELETE CASCADE
);

CREATE INDEX cell_certificate_upload_not_on_certificate_upload_id_idx
    ON cell_certificate_upload_not_on_certificate (cell_certificate_upload_id);

COMMENT ON TABLE cell_certificate_upload_not_on_certificate IS 'Location keys of certifiable cells that had no row in a cell certificate upload and so were carried onto the new certificate at their current values. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.cell_certificate_upload_id IS 'The upload this cell was missing from. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.location_key IS 'Location key of the cell that was not on the uploaded certificate. [Sensitivity: OFFICIAL-SENSITIVE]';
