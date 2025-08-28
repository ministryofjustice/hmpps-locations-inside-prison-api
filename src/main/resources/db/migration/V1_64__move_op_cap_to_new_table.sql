CREATE TABLE signed_operation_capacity(
    id serial not null constraint signed_operation_capacity_pk primary key,
    prison_id varchar(5) not null unique,
    signed_operation_capacity int not null default 0,
    pending_signed_operation_capacity int,
    certification_approval_request_id UUID,
    when_updated timestamp not null,
    updated_by varchar(255) not null,
CONSTRAINT fk_signed_operation_capacity_approval_request FOREIGN KEY (certification_approval_request_id) REFERENCES certification_approval_request (id) on delete cascade
);

-- Create an index on the foreign key
CREATE INDEX signed_operation_capacity_certification_approval_request_idx ON signed_operation_capacity (certification_approval_request_id);

create unique index signed_operation_capacity_prison_id_idx on signed_operation_capacity(prison_id);

insert into signed_operation_capacity (prison_id, when_updated, updated_by)
select prison_id,  when_updated, updated_by
from prison_configuration;

alter table prison_configuration drop column signed_operation_capacity;