CREATE TABLE capacity
(
    id                   SERIAL not null
        constraint capacity_pk primary key,
    capacity             int    not null default 0,
    operational_capacity int    not null default 0
);

