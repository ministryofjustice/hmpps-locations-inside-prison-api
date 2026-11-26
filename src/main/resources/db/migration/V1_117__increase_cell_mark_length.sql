ALTER TABLE location ALTER COLUMN cell_mark TYPE VARCHAR(30);
ALTER TABLE cell_certificate_location ALTER COLUMN cell_mark TYPE VARCHAR(30);
ALTER TABLE certification_approval_request ALTER COLUMN cell_mark TYPE VARCHAR(30);
ALTER TABLE certification_approval_request ALTER COLUMN current_cell_mark TYPE VARCHAR(30);
ALTER TABLE cell_certificate_upload_location ALTER COLUMN cell_mark TYPE VARCHAR(30);
ALTER TABLE cell_certificate_upload_location ALTER COLUMN previous_cell_mark TYPE VARCHAR(30);
