ALTER TABLE location_attribute RENAME TO residential_attribute;
ALTER TABLE residential_attribute RENAME COLUMN usage_type TO attribute_type;
ALTER TABLE residential_attribute RENAME COLUMN usage_value TO attribute_value;

ALTER TABLE location_usage RENAME TO non_residential_usage;