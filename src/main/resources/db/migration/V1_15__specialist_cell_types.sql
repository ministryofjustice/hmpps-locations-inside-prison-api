DROP TABLE security_category;

CREATE TABLE specialist_cell
(
    id          SERIAL      not null
        constraint specialist_cell_pk primary key,
    location_id UUID        NOT NULL,
    specialist_cell_type  VARCHAR(80) NOT NULL,
    FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX specialist_cell_location_id_idx ON specialist_cell (location_id);