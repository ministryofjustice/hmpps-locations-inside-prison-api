CREATE TABLE location_history
(
    id             SERIAL       not null
        constraint location_history_pk primary key,
    location_id    UUID,
    attribute_name VARCHAR(80)  NOT NULL,
    old_value      VARCHAR(255) NULL,
    new_value      VARCHAR(255) NULL,
    amended_by     VARCHAR(255) NOT NULL,
    amended_date   TIMESTAMP    NOT NULL,

    FOREIGN KEY (location_id) REFERENCES location (id)
);

ALTER TABLE location ALTER COLUMN code type VARCHAR(12)