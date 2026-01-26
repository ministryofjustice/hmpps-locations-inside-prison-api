ALTER TABLE certification_approval_request ADD COLUMN deactivated_reason varchar(30) NULL;
ALTER TABLE certification_approval_request ADD COLUMN deactivation_reason_description varchar(255) NULL;
ALTER TABLE certification_approval_request ADD COLUMN proposed_reactivation_date date NULL;
ALTER TABLE certification_approval_request ADD COLUMN planet_fm_reference varchar(60) NULL;
