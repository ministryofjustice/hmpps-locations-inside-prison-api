CREATE INDEX location_history_location_id_amend ON location_history (location_id, amended_date, amended_by);

DROP INDEX idx_location_prison_id_path_hierarchy;
