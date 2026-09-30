-- MAPA-400: the import report (and its preview) says what the import does to the certificate for every cell.
-- A cell not in the uploaded file is either carried forward from the current certificate, unchanged (MAPA-414),
-- or added to the certificate. And a cell in the file can change on the certificate even when Residential
-- locations does not, so each row records what the current certificate held for it.

ALTER TABLE cell_certificate_upload
    ADD COLUMN carried_forward_records INT NOT NULL DEFAULT 0;

ALTER TABLE cell_certificate_upload_not_on_certificate
    ADD COLUMN on_current_certificate BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN current_certified_max_capacity             INT NULL,
    ADD COLUMN current_certified_working_capacity         INT NULL,
    ADD COLUMN current_certified_normal_accommodation     INT NULL;

COMMENT ON COLUMN cell_certificate_upload.carried_forward_records IS 'Of the cells not in the upload, how many were already on the current certificate and carried forward unchanged. The rest were added to the certificate. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.on_current_certificate IS 'True when the cell was on the current certificate and carried forward unchanged; false when it was added to the certificate. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_location.current_certified_max_capacity IS 'The max capacity the prison''s current certificate recorded for this cell when the upload was processed; null when the cell was not on it. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload_location.current_certified_working_capacity IS 'The working capacity the prison''s current certificate recorded for this cell when the upload was processed; null when the cell was not on it. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload_location.current_certified_normal_accommodation IS 'The certified normal accommodation the prison''s current certificate recorded for this cell when the upload was processed; null when the cell was not on it. [Sensitivity: OFFICIAL-SENSITIVE]';
