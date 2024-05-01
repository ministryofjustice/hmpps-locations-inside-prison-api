ALTER TABLE location ADD COLUMN archived boolean NOT NULL default false;
ALTER TABLE location DROP COLUMN specialist_cell_type;
ALTER TABLE location alter COLUMN other_converted_cell_type type varchar(255);

CREATE UNIQUE INDEX specialist_cell_location_uk ON specialist_cell(location_id, specialist_cell_type);
CREATE UNIQUE INDEX cell_used_uk ON cell_used_for (location_id, used_for);
