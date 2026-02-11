-- Create index on path_hierarchy for efficient querying
CREATE INDEX cell_certificate_location_parent_location_idx ON cell_certificate_location (parent_location_id);
