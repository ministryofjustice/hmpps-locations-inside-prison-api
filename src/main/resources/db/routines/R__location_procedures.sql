CREATE OR REPLACE PROCEDURE delete_location_for_prison(IN p_prison_id varchar(6))
    LANGUAGE SQL
BEGIN
    ATOMIC
    DELETE
    FROM capacity c
    where EXISTS (select 1 from location l where l.capacity_id = c.id and l.prison_id = p_prison_id);

    DELETE
    FROM certification c
    where NOT EXISTS (select 1 from location l where l.certification_id = c.id);

    DELETE FROM location l where l.prison_id = p_prison_id;

    DELETE FROM prison_signed_operation_capacity where prison_id = p_prison_id;
END;

CREATE OR REPLACE FUNCTION map_accommodation_type(IN p_accommodation_type varchar) RETURNS varchar
AS $$
    BEGIN
        RETURN CASE
                   WHEN p_accommodation_type = 'NORMAL_ACCOMMODATION' THEN 'NORMAL_ACCOMMODATION'
                   WHEN p_accommodation_type = 'CARE_AND_SEPARATION' THEN 'SEGREGATION'
                   WHEN p_accommodation_type = 'HEALTHCARE_INPATIENTS' THEN 'HEALTHCARE'
            END;
    end;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_cell(IN p_code varchar,
                                        IN p_prison_id varchar,
                                        IN p_parent_path varchar,
                                        IN p_username varchar,
                                        IN p_max_cap integer = 1,
                                        IN p_working_cap integer = 1,
                                        IN p_accommodation_type varchar(60) = 'NORMAL_ACCOMMODATION',
                                        IN p_cell_type varchar(80) = null,
                                        IN p_used_for varchar(80) = 'STANDARD_ACCOMMODATION',
                                        IN p_certified boolean = true,
                                        IN p_active boolean = true
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
DECLARE v_current_parent_id UUID;
DECLARE v_capacity_id bigint;
DECLARE v_certification_id bigint;

BEGIN
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;

    INSERT INTO capacity (max_capacity, working_capacity) VALUES (p_max_cap, p_working_cap) returning id INTO v_capacity_id;
    INSERT INTO certification (certified, capacity_of_certified_cell) VALUES (p_certified, p_max_cap)  returning id INTO v_certification_id;

    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, active,
                          capacity_id, certification_id,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, concat(p_parent_path,'-',p_code), p_code, 'CELL', v_current_parent_id, p_active, v_capacity_id, v_certification_id,
            p_accommodation_type, map_accommodation_type(p_accommodation_type),
            now(), now(), p_username) RETURNING id INTO v_location_id;

    IF p_used_for IS NOT NULL THEN
        INSERT INTO cell_used_for (location_id, used_for) values (v_location_id, p_used_for);
    END IF;

    IF p_cell_type IS NOT NULL THEN
        INSERT INTO specialist_cell (location_id, specialist_cell_type) values (v_location_id, p_cell_type);
    END IF;

    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_inactive_cell(IN p_code varchar,
                                       IN p_prison_id varchar,
                                       IN p_parent_path varchar,
                                       IN p_username varchar,
                                       IN p_deactivation_reason varchar,
                                       IN p_deactivation_desc varchar,
                                       IN p_max_cap integer = 1,
                                       IN p_working_cap integer = 1
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;

BEGIN

    Select create_cell(p_code := p_code, p_prison_id := p_prison_id, p_parent_path := p_parent_path, p_username := p_username, p_max_cap := p_max_cap, p_working_cap := p_working_cap, p_active := false)
    INTO v_location_id;

    UPDATE location set deactivated_reason = p_deactivation_reason, deactivation_reason_description = p_deactivation_desc,
                        deactivated_date = now(), deactivated_by = p_username, planet_fm_reference = concat('PFM-',p_code), proposed_reactivation_date = current_date+interval '50 days'
    WHERE id = v_location_id;
    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_non_res_cell(IN p_code varchar,
                                                IN p_prison_id varchar,
                                                IN p_parent_path varchar,
                                                IN p_username varchar,
                                                IN p_non_res_cell_type varchar
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
    DECLARE v_current_parent_id UUID;
BEGIN

    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;

    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, active, converted_cell_type, other_converted_cell_type,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, concat(p_parent_path,'-',p_code), p_code, 'CELL', v_current_parent_id, true,
            p_non_res_cell_type, null,
            'NORMAL_ACCOMMODATION', map_accommodation_type('NORMAL_ACCOMMODATION'),
            now(), now(), p_username) RETURNING id INTO v_location_id;

    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_archive_location(IN p_code varchar,
                                                   IN p_prison_id varchar,
                                                   IN p_parent_path varchar,
                                                   IN p_username varchar,
                                                   IN p_archive_reason varchar,
                                                   IN p_location_type varchar = 'CELL'
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
    DECLARE v_current_parent_id UUID;
    DECLARE v_path_hierarchy varchar;
BEGIN

    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;
    if (p_parent_path is not null) then
        v_path_hierarchy := concat(p_parent_path,'-',p_code);
    else
        v_path_hierarchy := p_code;
    end if;

    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, active, archived, archived_reason,
                          deactivated_by, deactivated_date,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, v_path_hierarchy, p_code, p_location_type, v_current_parent_id, false, true, p_archive_reason,
            p_username, now(),
            'NORMAL_ACCOMMODATION', map_accommodation_type('NORMAL_ACCOMMODATION'),
            now(), now(), p_username) RETURNING id INTO v_location_id;

    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE PROCEDURE setup_prison_demo_locations(IN p_prison_id varchar(6), IN p_username varchar(80))
 AS $$
    DECLARE v_current_parent_id UUID;
BEGIN
    call delete_location_for_prison (p_prison_id := p_prison_id);

    -- Wings A to D, S and H
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'A', 'A', 'WING', null, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'B', 'B', 'WING', null, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'C', 'C', 'WING', null, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'D', 'D', 'WING', null, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'STANDARD_ACCOMMODATION' from location l where l.prison_id = p_prison_id and path_hierarchy in ('A', 'B', 'C') ;

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'FIRST_NIGHT_CENTRE' from location l where l.prison_id = p_prison_id and path_hierarchy in ('B') ;

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'VULNERABLE_PRISONERS' from location l where l.prison_id = p_prison_id and path_hierarchy in ('D') ;

    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'S', 'S', 'WING', null, 'Separation Unit', 'CARE_AND_SEPARATION', 'SEGREGATION', now(), now(), p_username);

    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'H', 'H', 'WING', null, 'Healthcare', 'HEALTHCARE_INPATIENTS', 'HEALTHCARE', now(), now(), p_username);


    -- Landing A
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'A';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'A-1', '1', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'A-2', '2', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'A-3', '3', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'STANDARD_ACCOMMODATION' from location l where l.prison_id = p_prison_id and path_hierarchy in ('A-1', 'A-2', 'A-3') ;

    -- Landing B
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'B';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'B-1', '1', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'B-2', '2', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'B-3', '3', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'STANDARD_ACCOMMODATION' from location l where l.prison_id = p_prison_id and path_hierarchy in ('B-1', 'B-2') ;

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'FIRST_NIGHT_CENTRE' from location l where l.prison_id = p_prison_id and path_hierarchy in ('B-3') ;


    -- Landing C
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'C';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'C-1', '1', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'C-2', '2', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'C-3', '3', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'STANDARD_ACCOMMODATION' from location l where l.prison_id = p_prison_id and path_hierarchy in ('C-1', 'C-2', 'C-3') ;


    -- Landing D
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'D';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'D-1', '1', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'D-2', '2', 'LANDING', v_current_parent_id, null, 'NORMAL_ACCOMMODATION', 'NORMAL_ACCOMMODATION', now(), now(), p_username);

    INSERT INTO cell_used_for (location_id, used_for)
    SELECT id, 'VULNERABLE_PRISONERS' from location l where l.prison_id = p_prison_id and path_hierarchy in ('D-1', 'D-2') ;


    -- Landing S
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'S';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'S-1', '1', 'LANDING', v_current_parent_id, null, 'CARE_AND_SEPARATION', 'SEGREGATION', now(), now(), p_username);


    -- Landing H
    SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = 'H';
    INSERT INTO location (prison_id, path_hierarchy, code, location_type, parent_id, local_name,
                          accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
    values (p_prison_id, 'H-1', '1', 'LANDING', v_current_parent_id, null, 'HEALTHCARE_INPATIENTS', 'HEALTHCARE', now(), now(), p_username);

    -- cells in A-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_cell_type := 'ACCESSIBLE_CELL', p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_cell_type := 'ACCESSIBLE_CELL', p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_inactive_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'PEST', p_deactivation_desc := 'Bed bugs');
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_non_res_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_non_res_cell_type := 'OFFICE');
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_cell_type := 'LISTENER_CRISIS', p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in A-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_inactive_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'DAMAGED', p_deactivation_desc := 'Flooded');
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := 'CONSTANT_SUPERVISION');
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := 'LISTENER_CRISIS');
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_inactive_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'MAINTENANCE', p_deactivation_desc := 'No running water');
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in A-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in B-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in B-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in B-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_archive_location(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');
    PERFORM create_archive_location(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');
    PERFORM create_archive_location(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');


    -- cells in C-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in C-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in C-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_archive_location(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_archive_reason := 'Merged to C-3-15');
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in D-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- cells in D-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2);

    -- D-3 archived cells
    PERFORM create_archive_location(p_code := '3', p_prison_id := p_prison_id, p_parent_path := 'D', p_username := p_username, p_archive_reason := 'Closure', p_location_type := 'LANDING');
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);

    -- cells in S-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '016', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '017', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '018', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '019', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '020', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);

    -- cells in H-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0);

    -- setup the signed op capacity
    insert into prison_signed_operation_capacity (signed_operation_capacity, prison_id, when_updated, updated_by)
    select SUM(COALESCE(c.max_capacity, 0)), l.prison_id, now(), p_username from location l left join capacity c on c.id = l.capacity_id and l.active = true and l.archived = false where l.prison_id = p_prison_id group by l.prison_id;
END;
$$ LANGUAGE plpgsql;

