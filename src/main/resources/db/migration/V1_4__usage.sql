alter table capacity
    drop column current_occupancy;

CREATE TABLE location_usage
(
    id          SERIAL      not null
        constraint location_usage_pk primary key,
    location_id UUID        NOT NULL,
    usage_type  VARCHAR(60) NOT NULL,
    capacity    INT         NULL,
    sequence    INT         NOT NULL DEFAULT 99,

    FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX location_usage_location_id_idx
    ON location_usage (location_id);