ALTER TABLE location ADD COLUMN deactivated_by varchar(80);
ALTER TABLE location ADD COLUMN when_deactivated timestamp;