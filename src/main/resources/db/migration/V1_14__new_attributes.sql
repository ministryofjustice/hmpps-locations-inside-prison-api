CREATE TABLE cell_used_for
(
    id          SERIAL      not null
        constraint cell_used_for_pk primary key,
    location_id UUID        NOT NULL,
    used_for  VARCHAR(80) NOT NULL,
    FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX cell_used_for_location_id_idx ON cell_used_for (location_id);

CREATE TABLE security_category
(
    id          SERIAL      not null
        constraint security_category_pk primary key,
    location_id UUID        NOT NULL,
    category  VARCHAR(80) NOT NULL,
    FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX security_category_location_id_idx ON security_category (location_id);

ALTER TABLE location ADD COLUMN accommodation_type VARCHAR(60);

ALTER TABLE location ADD COLUMN specialist_cell_type VARCHAR(60);
