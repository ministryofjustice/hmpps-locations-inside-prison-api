ALTER TABLE location DROP COLUMN when_deactivated;

UPDATE location set deactivated_by = updated_by where deactivated_reason is not null;