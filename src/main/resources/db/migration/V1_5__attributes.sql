CREATE TABLE location_attribute
(
    id          SERIAL      not null
        constraint location_attribute_pk primary key,
    location_id UUID        NOT NULL,
    usage_type  VARCHAR(60) NOT NULL,
    usage_value VARCHAR(60) NOT NULL,
    FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX location_attribute_location_id_idx ON location_attribute (location_id);