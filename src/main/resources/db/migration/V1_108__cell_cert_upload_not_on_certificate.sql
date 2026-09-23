-- An import only certifies the cells it lists, but the certificate generated at the end covers
-- every certifiable cell in the prison, so any live cell missing from the upload is carried onto
-- the new certificate at its current values. Record which cells that happened to, and the values
-- they were carried on at, so the ingestion report can disclose it, rather than silently changing
-- what has been certified.

ALTER TABLE cell_certificate_upload
    ADD COLUMN not_on_certificate_records INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN cell_certificate_upload.not_on_certificate_records IS 'How many certifiable cells had no row in the upload and so were carried onto the new certificate at their current values. [Sensitivity: NONE]';

CREATE TABLE cell_certificate_upload_not_on_certificate
(
    id                              UUID PRIMARY KEY,
    cell_certificate_upload_id      UUID         NOT NULL,
    location_id                     UUID         NOT NULL,
    location_key                    VARCHAR(255) NOT NULL,
    max_capacity                    INT          NOT NULL,
    working_capacity                INT          NOT NULL,
    certified_normal_accommodation  INT          NOT NULL,
    CONSTRAINT fk_cell_certificate_upload_not_on_certificate_upload
        FOREIGN KEY (cell_certificate_upload_id) REFERENCES cell_certificate_upload (id) ON DELETE CASCADE
);

CREATE INDEX cell_certificate_upload_not_on_certificate_upload_id_idx
    ON cell_certificate_upload_not_on_certificate (cell_certificate_upload_id);

COMMENT ON TABLE cell_certificate_upload_not_on_certificate IS 'Certifiable cells that had no row in a cell certificate upload and so were carried onto the new certificate at their current values. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.id IS 'Primary key. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.cell_certificate_upload_id IS 'The upload this cell was missing from. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.location_id IS 'ID of the cell that was not on the uploaded certificate, so the report can link to it. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.location_key IS 'Location key of the cell that was not on the uploaded certificate. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.max_capacity IS 'The max capacity the cell was carried onto the new certificate at. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.working_capacity IS 'The working capacity the cell was carried onto the new certificate at. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.certified_normal_accommodation IS 'The certified normal accommodation the cell was carried onto the new certificate at. [Sensitivity: NONE]';
