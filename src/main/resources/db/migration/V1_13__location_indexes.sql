DROP INDEX idx_location_residential_housing_type;
CREATE INDEX location_housing_type_idx ON location (residential_housing_type, location_type);
CREATE INDEX location_history_location_id_idx ON location_history (location_id);