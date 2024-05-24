CREATE TABLE prison_signed_operation_capacity
(
    id serial not null constraint signed_operation_capacity_pk primary key,
    signed_operation_capacity int not null default 0,
    prison_id varchar(5) not null unique,
    when_updated timestamp not null,
    updated_by varchar(255) not null
);