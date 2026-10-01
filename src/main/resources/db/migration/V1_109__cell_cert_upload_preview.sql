-- MAPA-405: every cell certificate import is previewed first. A preview is stored in the same tables as an
-- import and processed the same way, but every change it works out is undone, so nothing is changed.
-- Continuing a preview copies its rows into a new import that links back to it.

ALTER TABLE cell_certificate_upload
    ADD COLUMN mode                                     VARCHAR(20) NOT NULL DEFAULT 'IMPORT',
    ADD COLUMN preview_upload_id                        UUID        NULL REFERENCES cell_certificate_upload (id) ON DELETE SET NULL,
    ADD COLUMN current_max_capacity                     INT         NULL,
    ADD COLUMN current_working_capacity                 INT         NULL,
    ADD COLUMN current_certified_normal_accommodation   INT         NULL,
    ADD COLUMN projected_max_capacity                   INT         NULL,
    ADD COLUMN projected_working_capacity               INT         NULL,
    ADD COLUMN projected_certified_normal_accommodation INT         NULL;

CREATE INDEX cell_certificate_upload_preview_upload_id_idx ON cell_certificate_upload (preview_upload_id);

-- Only one active import may run per prison at a time. Previews change nothing, so they neither block an
-- import nor each other.
DROP INDEX cell_certificate_upload_active_prison_idx;
CREATE UNIQUE INDEX cell_certificate_upload_active_prison_idx
    ON cell_certificate_upload (prison_id)
    WHERE status IN ('PENDING', 'STARTED') AND mode = 'IMPORT';

COMMENT ON COLUMN cell_certificate_upload.mode IS 'PREVIEW works out what the import would do without changing anything; IMPORT changes the locations and creates a new cell certificate. See CellCertificateUploadMode in reference-data.csv. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload.preview_upload_id IS 'On an import, the preview it was continued from. Cleared if the preview is deleted. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload.current_max_capacity IS 'On a finished preview, the total max capacity on the prison''s current cell certificate. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload.current_working_capacity IS 'On a finished preview, the total working capacity on the prison''s current cell certificate. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload.current_certified_normal_accommodation IS 'On a finished preview, the total certified normal accommodation on the prison''s current cell certificate. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload.projected_max_capacity IS 'On a finished preview, the total max capacity the new cell certificate would have if the import went ahead. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload.projected_working_capacity IS 'On a finished preview, the total working capacity the new cell certificate would have if the import went ahead. [Sensitivity: OFFICIAL-SENSITIVE]';
COMMENT ON COLUMN cell_certificate_upload.projected_certified_normal_accommodation IS 'On a finished preview, the total certified normal accommodation the new cell certificate would have if the import went ahead. [Sensitivity: OFFICIAL-SENSITIVE]';
