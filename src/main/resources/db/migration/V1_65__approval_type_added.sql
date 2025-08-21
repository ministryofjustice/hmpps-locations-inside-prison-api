alter table certification_approval_request
add column approval_type varchar(30) NOT NULL DEFAULT 'DRAFT',
ADD COLUMN signed_operation_capacity_change INT NOT NULL DEFAULT 0;