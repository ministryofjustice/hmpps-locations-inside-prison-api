CREATE TABLE signed_operation_capacity(
    id serial not null constraint signed_operation_capacity_pk primary key,
    prison_id varchar(5) not null unique,
    signed_operation_capacity int not null default 0,
    when_updated timestamp not null,
    updated_by varchar(255) not null
);

create unique index signed_operation_capacity_prison_id_idx on signed_operation_capacity(prison_id);

insert into signed_operation_capacity (prison_id, when_updated, updated_by)
select prison_id,  when_updated, updated_by
from prison_configuration;

alter table prison_configuration drop column signed_operation_capacity;