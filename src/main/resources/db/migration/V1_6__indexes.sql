CREATE INDEX idx_location_residential_housing_type ON location (residential_housing_type);

create unique index location_usage_location_id_usage_type_uindex
    on location_usage (location_id, usage_type);

create unique index location_attribute_location_id_usage_type_usage_value_idx
    on location_attribute (location_id, usage_type, usage_value);