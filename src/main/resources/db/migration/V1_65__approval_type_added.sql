alter table certification_approval_request
add column approval_type varchar(30) NOT NULL DEFAULT 'DRAFT',
add column signed_operation_capacity_change INT NOT NULL DEFAULT 0,
add column signed_operation_capacity_id bigint null;