INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A', 'A', 'WING', null, 'Wing A', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'B', 'B', 'WING', null, 'Wing B', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'C', 'C', 'WING', null, 'Wing C', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1', '1', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A'), 'Landing 1', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2', '2', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A'), 'Landing 2', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'B-1', '1', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'B'), 'Landing 1', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'B-2', '2', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'B'), 'Landing 2', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'B-3', '3', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'C'), 'Landing 1', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'C-1', '1', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'C'), 'Landing 2', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'C-2', '2', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'C'), 'Landing 3', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'C-3', '3', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'C'), 'Landing 4', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'C-4', '4', 'LANDING', (select id from location where prison_id = 'LEI' and path_hierarchy = 'B'), 'Landing 2', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1-001', '001', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1-002', '002', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1-003', '003', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1-004', '004', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-1-005', '005', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-1-001';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-001'), 'STANDARD_ACCOMMODATION');
INSERT INTO specialist_cell (location_id, specialist_cell_type) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-001'), 'ESCAPE_LIST');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (2,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-1-002';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-002'), 'PIPE');
INSERT INTO specialist_cell (location_id, specialist_cell_type) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-002'), 'WHEELCHAIR_ACCESSIBLE');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-1-003';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-003'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-1-004';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-004'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-1-005';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-1-005'), 'STANDARD_ACCOMMODATION');


INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2-001', '001', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2-002', '002', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2-003', '003', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2-004', '004', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');
INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
values ('LEI', 'A-2-005', '005', 'CELL', (select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2'), 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), 'LOCATION_RO');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-2-001';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2-001'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-2-002';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2-002'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-2-003';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2-003'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-2-004';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2-004'), 'STANDARD_ACCOMMODATION');

INSERT INTO capacity (max_capacity, working_capacity) VALUES (1,1);
INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (true, 1);
UPDATE location set capacity_id = currval('capacity_id_seq'), certification_id = currval('certification_id_seq') WHERE prison_id = 'LEI' and path_hierarchy = 'A-2-005';
INSERT INTO cell_used_for (location_id, used_for) values ((select id from location where prison_id = 'LEI' and path_hierarchy = 'A-2-005'), 'STANDARD_ACCOMMODATION');


