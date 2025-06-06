ALTER TABLE certification_approval_request
    ADD COLUMN certified_normal_accommodation_change INT NOT NULL DEFAULT 0,
    ADD COLUMN working_capacity_change               INT NOT NULL DEFAULT 0,
    ADD COLUMN max_capacity_change                   INT NOT NULL DEFAULT 0;

ALTER TABLE cell_certificate_location
    DROP status;


ALTER TABLE certification
    RENAME COLUMN capacity_of_certified_cell TO certified_normal_accommodation;
ALTER TABLE cell_certificate
    RENAME COLUMN total_capacity_of_certified_cell TO total_certified_normal_accommodation;
ALTER TABLE cell_certificate_location
    RENAME COLUMN capacity_of_certified_cell TO certified_normal_accommodation;
