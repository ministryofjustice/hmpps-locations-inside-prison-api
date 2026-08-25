-- MAPA-319: ingestion no longer forces a cell's working capacity (or max capacity) to the uploaded
-- value. Where the uploaded certified value is retained on the certificate but the location keeps
-- its own value, the row records the difference so it can be reviewed later.

ALTER TABLE cell_certificate_upload
    ADD COLUMN discrepancy_records INT NOT NULL DEFAULT 0;

ALTER TABLE cell_certificate_upload_location
    ADD COLUMN working_capacity_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN max_capacity_mismatch     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN certified_normal_accommodation_mismatch BOOLEAN NOT NULL DEFAULT FALSE;
