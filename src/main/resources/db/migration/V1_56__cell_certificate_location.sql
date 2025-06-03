-- Create the cell_certificate_location table
CREATE TABLE cell_certificate_location
(
    id                         UUID PRIMARY KEY,
    cell_certificate_id        UUID         NOT NULL,
    location_code              VARCHAR(40)  NOT NULL,
    path_hierarchy             VARCHAR(255) NOT NULL,
    capacity_of_certified_cell INT,
    working_capacity           INT,
    max_capacity               INT,
    in_cell_sanitation         BOOLEAN,
    location_type              VARCHAR(40)  NOT NULL,
    specialist_cell_types      VARCHAR(255),
    cell_mark                  VARCHAR(12)  NULL,
    level                      INT          NOT NULL,
    local_name                 VARCHAR(100) NULL,
    parent_location_id         UUID         NULL,
    status                     VARCHAR(20)  NOT NULL,
    CONSTRAINT fk_cell_certificate_location_cell_certificate FOREIGN KEY (cell_certificate_id) REFERENCES cell_certificate (id) on delete cascade
);

-- Create index on foreign key
CREATE INDEX cell_certificate_location_cell_certificate_id_idx ON cell_certificate_location (cell_certificate_id);

-- Create index on path_hierarchy for efficient querying
CREATE INDEX cell_certificate_location_path_hierarchy_idx ON cell_certificate_location (path_hierarchy);
