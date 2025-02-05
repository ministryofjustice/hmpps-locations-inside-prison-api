ALTER TABLE prison_signed_operation_capacity RENAME TO prison_configuration;

ALTER TABLE prison_configuration DROP CONSTRAINT signed_operation_capacity_pk;

ALTER TABLE prison_configuration DROP COLUMN IF EXISTS id;

ALTER TABLE prison_configuration ADD PRIMARY KEY (prison_id);

ALTER TABLE prison_configuration ADD COLUMN resi_location_service_active boolean NOT NULL DEFAULT FALSE;

ALTER TABLE prison_configuration ADD COLUMN include_segregation_in_roll_count boolean NOT NULL DEFAULT FALSE;

update prison_configuration
set include_segregation_in_roll_count = true
where prison_id = 'GMI';

update prison_configuration
set resi_location_service_active = true
where prison_id IN ('BCI','NMI','WII','LII','HLI');
