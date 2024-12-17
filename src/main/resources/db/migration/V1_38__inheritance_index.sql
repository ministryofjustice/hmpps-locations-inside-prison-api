DROP INDEX location_housing_type_idx;
CREATE INDEX location_housing_type_idx ON location (residential_housing_type, location_type, code);