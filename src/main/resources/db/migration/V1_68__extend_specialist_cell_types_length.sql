-- Increase the size of specialist_cell_types to 4000 characters
-- Context: HMPPS Locations Inside Prison API
-- Reason: Need to store more specialist cell types details per location

ALTER TABLE cell_certificate_location
    ALTER COLUMN specialist_cell_types TYPE VARCHAR(4000);
