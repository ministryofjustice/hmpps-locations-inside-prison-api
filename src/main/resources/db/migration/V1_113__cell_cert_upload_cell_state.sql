-- MAPA-428: record whether each cell was inactive, and why, and its specialist cell types when an import or preview
-- ran. Both commonly explain why a cell's capacity differs from its certificate, so the report shows them rather than
-- leaving the user to open each location. Recorded rather than looked up, so a preview still explains its results after
-- the prison has changed the cell.

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN inactive                        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN deactivated_reason              VARCHAR(30)  NULL,
    ADD COLUMN deactivation_reason_description VARCHAR(255) NULL,
    ADD COLUMN specialist_cell_types           VARCHAR(255) NULL;

ALTER TABLE cell_certificate_upload_not_on_certificate
    ADD COLUMN inactive                        BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN deactivated_reason              VARCHAR(30)  NULL,
    ADD COLUMN deactivation_reason_description VARCHAR(255) NULL,
    ADD COLUMN specialist_cell_types           VARCHAR(255) NULL;

COMMENT ON COLUMN cell_certificate_upload_location.inactive IS 'The cell was temporarily deactivated, itself or through a location above it, when the upload was processed. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_location.deactivated_reason IS 'Why the cell was inactive when the upload was processed. See DeactivatedReason in reference-data.csv. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_location.deactivation_reason_description IS 'Free text expanding on deactivated_reason, copied from the location. Assume it can name a prisoner. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN cell_certificate_upload_location.specialist_cell_types IS 'Comma-separated specialist cell types the cell had when the upload was processed. See SpecialistCellType in reference-data.csv. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.inactive IS 'The cell was temporarily deactivated, itself or through a location above it, when the upload was processed. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.deactivated_reason IS 'Why the cell was inactive when the upload was processed. See DeactivatedReason in reference-data.csv. [Sensitivity: NONE]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.deactivation_reason_description IS 'Free text expanding on deactivated_reason, copied from the location. Assume it can name a prisoner. [Sensitivity: PERSONAL]';
COMMENT ON COLUMN cell_certificate_upload_not_on_certificate.specialist_cell_types IS 'Comma-separated specialist cell types the cell had when the upload was processed. See SpecialistCellType in reference-data.csv. [Sensitivity: NONE]';
