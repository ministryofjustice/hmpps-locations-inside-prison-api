
INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'LOCATE_FLAT_CELL' from residential_attribute ra WHERE attribute_value = 'LOCATE_FLAT'
                                                                   and not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.location_id and specialist_cell_type = 'LOCATE_FLAT_CELL');

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT location_id, 'SAFE_CELL' from residential_attribute ra WHERE attribute_value = 'SAFE_CELL'
                                                                           and not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.location_id and specialist_cell_type = 'SAFE_CELL');

delete from specialist_cell where specialist_cell_type = 'LOW_MOBILITY';