CREATE TABLE certification
(
    id                         SERIAL  not null
        constraint certification_pk primary key,
    certified                  boolean not null default false,
    capacity_of_certified_cell int     not null default 0
);