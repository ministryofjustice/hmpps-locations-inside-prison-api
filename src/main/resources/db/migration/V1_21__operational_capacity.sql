CREATE TABLE operational_capacity
(
    id serial not null
        constraint operational_capacity_pk primary key,
    capacity int not null default 0,
    prison_id varchar(3) not null unique,
    date_time timestamp not null,
    approved_by varchar(255) not null
);