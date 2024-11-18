DELETE FROM location;

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A', 'A', 'WING', 'RESIDENTIAL', null, 'Wing A', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-1', '1', 'LANDING', 'RESIDENTIAL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A'), 'Landing 1', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-1-001', '001', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-1-001';

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, local_name, parent_id, residential_housing_type, deactivated_by, planet_fm_reference, proposed_reactivation_date, deactivated_reason, deactivated_date, active, when_created, when_updated, updated_by)
values ('MDI', 'A-2', '2', 'LANDING', 'RESIDENTIAL', 'Landing 2', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A'), 'NORMAL_ACCOMMODATION', 'LOCATION_RO', 'FT9232/1', current_date + interval '100 days', 'MAINTENANCE', now(), false, now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, deactivated_by, planet_fm_reference, proposed_reactivation_date, deactivated_reason, deactivated_date, active, when_created, when_updated, updated_by)
values ('MDI', 'A-2-001', '001', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', 'LOCATION_RO', 'FT9232/1', current_date + interval '100 days', 'MAINTENANCE', now(), false,  now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-2-001';

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, deactivated_by, planet_fm_reference, proposed_reactivation_date, deactivated_reason, deactivation_reason_description, deactivated_date, active, when_created, when_updated, updated_by)
values ('MDI', 'A-2-002', '002', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', 'LOCATION_RO', 'FT9232/1', current_date + interval '100 days', 'MAINTENANCE', 'More info', now(), false,  now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-2-002';

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, deactivated_by, planet_fm_reference, proposed_reactivation_date, deactivated_reason, deactivation_reason_description, deactivated_date, active, when_created, when_updated, updated_by)
values ('MDI', 'A-2-003', '003', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', 'LOCATION_RO', 'FT9232/1', current_date + interval '100 days', 'OTHER', 'Does not exist', now(), false,  now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-2-003';


INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-3', '3', 'LANDING', 'RESIDENTIAL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A'), 'Landing 3', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-3-001', '001', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-3'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-3-001';


INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-4', '4', 'LANDING', 'RESIDENTIAL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A'), 'Landing 4', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('MDI', 'A-4-001', '001', 'CELL', 'CELL', (select id from location where prison_id = 'MDI' and path_hierarchy = 'A-4'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'MDI' and path_hierarchy = 'A-4-001';

INSERT INTO cell_used_for (location_id, used_for)
SELECT id, 'STANDARD_ACCOMMODATION'
from location
where residential_housing_type IS NOT NULL and location_type = 'CELL' and accommodation_type = 'NORMAL_ACCOMMODATION';

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'DRY' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'DRY')
  and location_type = 'CELL' and active = true and converted_cell_type is null;

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'ESCAPE_LIST' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'ESCAPE_LIST')
  and location_type = 'CELL' and active = true and converted_cell_type is null;
