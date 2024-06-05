UPDATE specialist_cell ra
set specialist_cell_type = 'ACCESSIBLE_CELL' WHERE specialist_cell_type = 'WHEELCHAIR_ACCESSIBLE'
                                               and not exists (select 1 from specialist_cell sc where sc.location_id = ra.location_id and sc.specialist_cell_type = 'ACCESSIBLE_CELL');

delete  from specialist_cell where specialist_cell_type = 'WHEELCHAIR_ACCESSIBLE';

