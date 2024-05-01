ALTER TABLE location ADD COLUMN planet_fm_reference VARCHAR(60);
ALTER TABLE location ADD COLUMN converted_cell_type VARCHAR(60);
ALTER TABLE location ADD COLUMN other_converted_cell_type VARCHAR(255);
ALTER TABLE location ADD COLUMN archived boolean NOT NULL default false;

CREATE UNIQUE INDEX specialist_cell_location_uk ON specialist_cell(location_id, specialist_cell_type);

CREATE UNIQUE INDEX cell_used_uk ON cell_used_for (location_id, used_for);
