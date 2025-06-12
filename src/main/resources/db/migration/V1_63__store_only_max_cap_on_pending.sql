ALTER TABLE pending_location_change ADD COLUMN max_capacity integer;
ALTER TABLE pending_location_change DROP COLUMN capacity_id
