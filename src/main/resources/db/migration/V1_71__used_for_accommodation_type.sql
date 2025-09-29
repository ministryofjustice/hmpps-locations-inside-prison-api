ALTER TABLE cell_certificate_location
    ADD COLUMN used_for_types      VARCHAR(300),
    ADD COLUMN accommodation_types VARCHAR(120),
    ALTER COLUMN specialist_cell_types TYPE VARCHAR(300);

ALTER TABLE certification_approval_request_location
    ADD COLUMN used_for_types      VARCHAR(300),
    ADD COLUMN accommodation_types VARCHAR(120),
    ALTER COLUMN specialist_cell_types TYPE VARCHAR(300);

ALTER TABLE cell_certificate
    ADD COLUMN signed_operation_capacity INTEGER NOT NULL DEFAULT 0;