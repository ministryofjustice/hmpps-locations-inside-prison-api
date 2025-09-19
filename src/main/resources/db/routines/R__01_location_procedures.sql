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
    DELETE FROM prison_configuration where prison_id = p_prison_id;
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
                                        IN p_cell_type varchar(80)[] = ARRAY[]::varchar[],
                                        IN p_used_for varchar(80)[] = ARRAY['STANDARD_ACCOMMODATION'],
                                        IN p_certified boolean = true,
                                        IN p_status varchar(20) = 'ACTIVE'
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
DECLARE v_current_parent_id UUID;
DECLARE v_capacity_id bigint;
DECLARE v_certification_id bigint;
    DECLARE v_used_for varchar(80);
    DECLARE v_cell_type varchar(80);

BEGIN
    SELECT id INTO v_location_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = concat(p_parent_path,'-',p_code);
    IF FOUND THEN
        PERFORM update_cell(p_cell_path := concat(p_parent_path,'-',p_code), p_prison_id := p_prison_id, p_username := p_username, p_max_cap := p_max_cap, p_working_cap := p_working_cap, p_accommodation_type := p_accommodation_type, p_cell_type := p_cell_type, p_used_for := p_used_for, p_certified := p_certified, p_status := p_status);
    else
        SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;

        INSERT INTO capacity (max_capacity, working_capacity) VALUES (p_max_cap, p_working_cap) returning id INTO v_capacity_id;
        INSERT INTO certification (certified, certified_normal_accommodation) VALUES (p_certified, p_max_cap)  returning id INTO v_certification_id;

        INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, status,
                              capacity_id, certification_id,
                              accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
        values (p_prison_id, concat(p_parent_path,'-',p_code), p_code, 'CELL', 'CELL', v_current_parent_id, p_status, v_capacity_id, v_certification_id,
                p_accommodation_type, map_accommodation_type(p_accommodation_type),
                now(), now(), p_username) RETURNING id INTO v_location_id;

        FOREACH v_used_for IN ARRAY p_used_for
        LOOP
            INSERT INTO cell_used_for (location_id, used_for) values (v_location_id, v_used_for);
        END LOOP;

        FOREACH v_cell_type IN ARRAY p_cell_type
        LOOP
            INSERT INTO specialist_cell (location_id, specialist_cell_type) values (v_location_id, v_cell_type);
        END LOOP;
    END IF;

    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_cell(IN p_cell_path varchar,
                                       IN p_prison_id varchar,
                                       IN p_username varchar,
                                       IN p_max_cap integer = 1,
                                       IN p_working_cap integer = 1,
                                       IN p_accommodation_type varchar(60) = 'NORMAL_ACCOMMODATION',
                                       IN p_cell_type varchar(80)[] = ARRAY[]::varchar[],
                                       IN p_used_for varchar(80)[] = ARRAY['STANDARD_ACCOMMODATION'],
                                       IN p_certified boolean = true,
                                       IN p_status varchar(20) = 'ACTIVE'
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
    DECLARE v_capacity_id bigint;
    DECLARE v_certification_id bigint;
    DECLARE v_used_for varchar(80);
    DECLARE v_cell_type varchar(80);
BEGIN

    SELECT id, capacity_id, certification_id INTO v_location_id, v_capacity_id, v_certification_id
    from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_cell_path;

    UPDATE location
        SET accommodation_type = p_accommodation_type,
            residential_housing_type = map_accommodation_type(p_accommodation_type),
            status = p_status,
            updated_by = p_username,
            when_updated = now()
    WHERE id = v_location_id;

    IF p_max_cap IS NOT NULL THEN
        UPDATE capacity SET max_capacity = p_max_cap WHERE id = v_capacity_id;
    END IF;

    IF p_working_cap IS NOT NULL THEN
        UPDATE capacity SET working_capacity = p_working_cap WHERE id = v_capacity_id;
    END IF;

    UPDATE certification SET certified = p_certified WHERE id = v_certification_id;

    DELETE FROM cell_used_for WHERE location_id = v_location_id;
    FOREACH v_used_for IN ARRAY p_used_for
        LOOP
            INSERT INTO cell_used_for (location_id, used_for) values (v_location_id, v_used_for);
        END LOOP;


    DELETE FROM specialist_cell WHERE location_id = v_location_id;
    FOREACH v_cell_type IN ARRAY p_cell_type
        LOOP
            INSERT INTO specialist_cell (location_id, specialist_cell_type) values (v_location_id, v_cell_type);
        END LOOP;
    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_inactive_cell(IN p_code varchar,
                                       IN p_prison_id varchar,
                                       IN p_parent_path varchar,
                                       IN p_username varchar,
                                       IN p_deactivation_reason varchar,
                                       IN p_deactivation_desc varchar = null,
                                       IN p_max_cap integer = 1,
                                       IN p_working_cap integer = 1,
                                       IN p_cell_type varchar(80)[] = ARRAY[]::varchar[]
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;

BEGIN

    SELECT id INTO v_location_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = concat(p_parent_path,'-',p_code);
    IF FOUND THEN
        PERFORM update_cell(p_cell_path := concat(p_parent_path,'-',p_code), p_prison_id := p_prison_id, p_username := p_username, p_cell_type := p_cell_type, p_max_cap := p_max_cap, p_working_cap := p_working_cap, p_status := 'INACTIVE');
    ELSE
        Select create_cell(p_code := p_code, p_prison_id := p_prison_id, p_parent_path := p_parent_path, p_username := p_username, p_max_cap := p_max_cap, p_working_cap := p_working_cap, p_status := 'INACTIVE', p_cell_type := p_cell_type)
        INTO v_location_id;
    END IF;
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
    DECLARE v_capacity_id bigint;
    DECLARE v_certification_id bigint;
BEGIN

    SELECT id, capacity_id, certification_id INTO v_location_id, v_capacity_id, v_certification_id
    from location l where l.prison_id = p_prison_id and l.path_hierarchy = concat(p_parent_path,'-',p_code);
    IF FOUND THEN
        delete from cell_used_for where location_id = v_location_id;
        delete from specialist_cell where location_id = v_location_id;

        UPDATE location
            SET converted_cell_type = p_non_res_cell_type,
                other_converted_cell_type = null,
                capacity_id = null,
                certification_id = null,
                residential_housing_type = map_accommodation_type('NORMAL_ACCOMMODATION'),
                accommodation_type = 'NORMAL_ACCOMMODATION',
                updated_by = p_username,
                when_updated = now()
        WHERE id = v_location_id;

        delete from capacity where id = v_capacity_id;
        delete from certification where id = v_certification_id;

    ELSE
        SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;

        INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, status, converted_cell_type, other_converted_cell_type,
                              accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
        values (p_prison_id, concat(p_parent_path,'-',p_code), p_code, 'CELL', 'CELL', v_current_parent_id, 'ACTIVE',
                p_non_res_cell_type, null,
                'NORMAL_ACCOMMODATION', map_accommodation_type('NORMAL_ACCOMMODATION'),
                now(), now(), p_username) RETURNING id INTO v_location_id;
    END IF;
    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION convert_to_non_res_cell(
                                               IN p_prison_id varchar,
                                               IN p_path_hierarchy varchar,
                                               IN p_username varchar,
                                               IN p_non_res_cell_type varchar,
                                               IN p_other_converted_cell_type varchar
) RETURNS UUID
AS $$
    DECLARE v_location_id UUID;
    DECLARE v_capacity_id integer;
    DECLARE v_certification_id integer;
BEGIN

    SELECT id, capacity_id, certification_id INTO v_location_id, v_capacity_id, v_certification_id
    from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_path_hierarchy;

    UPDATE location
        SET converted_cell_type = p_non_res_cell_type,
            other_converted_cell_type = p_other_converted_cell_type,
            capacity_id = null,
            location_type = 'CELL',
            residential_housing_type = 'NORMAL_ACCOMMODATION',
            accommodation_type = 'OTHER_NON_RESIDENTIAL',
            updated_by = p_username,
            when_updated = now()
    WHERE id = v_location_id;

    delete from capacity where id = v_capacity_id;

    update certification
        set certified = false
    where id = v_certification_id;

    delete from cell_used_for where location_id = v_location_id;
    delete from specialist_cell where location_id = v_location_id;
    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_archive_location(IN p_code varchar,
                                                   IN p_prison_id varchar,
                                                   IN p_parent_path varchar,
                                                   IN p_username varchar,
                                                   IN p_archive_reason varchar,
                                                   IN p_location_type varchar = 'CELL',
                                                   IN p_location_type_discriminator varchar = 'CELL'
) RETURNS UUID
AS $$
DECLARE v_location_id UUID;
    DECLARE v_current_parent_id UUID;
    DECLARE v_path_hierarchy varchar;
    DECLARE v_capacity_id integer;
    DECLARE v_certification_id integer;
BEGIN

    SELECT id, capacity_id, certification_id INTO v_location_id, v_capacity_id, v_certification_id
    from location l where l.prison_id = p_prison_id and l.path_hierarchy = concat(p_parent_path,'-',p_code);
    IF FOUND THEN
        delete from cell_used_for where location_id = v_location_id;
        delete from specialist_cell where location_id = v_location_id;

        UPDATE location
        SET location_type_discriminator = p_location_type_discriminator,
            location_type = p_location_type,
            status = 'ARCHIVED',
            archived_reason = p_archive_reason,
            deactivated_by = p_username,
            deactivated_date = now(),
            converted_cell_type = null,
            other_converted_cell_type = null,
            capacity_id = null,
            certification_id = null,
            residential_housing_type = map_accommodation_type('NORMAL_ACCOMMODATION'),
            accommodation_type = 'NORMAL_ACCOMMODATION',
            updated_by = p_username,
            when_updated = now()
        WHERE id = v_location_id;

        delete from capacity where id = v_capacity_id;
        delete from certification where id = v_certification_id;

    ELSE
        SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_parent_path;
        if (p_parent_path is not null) then
            v_path_hierarchy := concat(p_parent_path,'-',p_code);
        else
            v_path_hierarchy := p_code;
        end if;

        INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, status, archived_reason,
                              deactivated_by, deactivated_date,
                              accommodation_type, residential_housing_type, when_created, when_updated, updated_by)
        values (p_prison_id, v_path_hierarchy, p_code, p_location_type, p_location_type_discriminator, v_current_parent_id, 'ARCHIVED', p_archive_reason,
                p_username, now(),
                'NORMAL_ACCOMMODATION', map_accommodation_type('NORMAL_ACCOMMODATION'),
                now(), now(), p_username) RETURNING id INTO v_location_id;
    END IF;

    return v_location_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_wing(IN p_prison_id varchar(6),
                                       IN p_wing_code varchar(6),
                                       IN p_username varchar(80),
                                       IN p_number_landings integer = 1,
                                       IN p_number_cells_per_landing integer = 10,
                                       IN p_local_name varchar(100) = null,
                                       IN p_max_cap integer = 1,
                                       IN p_working_cap integer = 1,
                                       IN p_accommodation_type varchar(60) = 'NORMAL_ACCOMMODATION',
                                       IN p_housing_type varchar(60) = 'NORMAL_ACCOMMODATION',
                                       IN p_used_for varchar(80)[] = ARRAY['STANDARD_ACCOMMODATION'],
                                       IN p_cell_type varchar(80)[] = ARRAY[]::varchar[]

) RETURNS UUID
AS $$
    DECLARE v_current_parent_id UUID;
    BEGIN
        INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name,
                              accommodation_type, residential_housing_type, when_created, when_updated, updated_by, status)
        values (p_prison_id, p_wing_code, p_wing_code, 'WING', 'RESIDENTIAL', null, p_local_name, p_accommodation_type, p_housing_type, now(), now(), p_username, 'ACTIVE');

        SELECT id INTO v_current_parent_id from location l where l.prison_id = p_prison_id and l.path_hierarchy = p_wing_code;
        FOR landing_number IN 1..p_number_landings LOOP
            INSERT INTO location (prison_id, path_hierarchy, code, location_type, location_type_discriminator, parent_id, local_name,
                                  accommodation_type, residential_housing_type, when_created, when_updated, updated_by, status)
            values (p_prison_id, concat(p_wing_code,'-',landing_number), landing_number, 'LANDING', 'RESIDENTIAL',v_current_parent_id, null, p_accommodation_type, p_housing_type, now(), now(), p_username, 'ACTIVE');

            FOR cell_number IN 1..p_number_cells_per_landing LOOP
                PERFORM create_cell(p_code := trim(to_char(cell_number, '000')), p_prison_id := p_prison_id, p_parent_path := concat(p_wing_code,'-',landing_number), p_username := p_username, p_max_cap := p_max_cap, p_working_cap := p_working_cap, p_used_for := p_used_for, p_cell_type := p_cell_type);
            END LOOP;
        END LOOP;

        return v_current_parent_id;
    END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE PROCEDURE setup_prison_demo_locations(IN p_prison_id varchar(6), IN p_username varchar(80))
 AS $$
BEGIN
    call delete_location_for_prison (p_prison_id := p_prison_id);

    -- Wings A to D, S and H
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'A', p_number_landings := 3, p_number_cells_per_landing := 15, p_working_cap := 2, p_max_cap := 2, p_username := p_username);
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'B', p_number_landings := 3, p_number_cells_per_landing := 15, p_working_cap := 2, p_max_cap := 2, p_used_for := array['STANDARD_ACCOMMODATION', 'FIRST_NIGHT_CENTRE'],  p_username := p_username);
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'C', p_number_landings := 3, p_number_cells_per_landing := 15, p_working_cap := 2, p_max_cap := 2, p_username := p_username);
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'D', p_number_landings := 2, p_number_cells_per_landing := 15, p_used_for := array ['VULNERABLE_PRISONERS'], p_username := p_username);
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'S', p_number_landings := 1, p_number_cells_per_landing := 20, p_working_cap := 0, p_max_cap := 1, p_used_for := array[]::varchar[], p_username := p_username, p_local_name := 'Separation Unit', p_accommodation_type := 'CARE_AND_SEPARATION', p_housing_type := 'SEGREGATION');
    PERFORM create_wing(p_prison_id := p_prison_id, p_wing_code := 'H', p_number_landings := 1, p_number_cells_per_landing := 10, p_working_cap := 0, p_max_cap := 2, p_used_for := array[]::varchar[], p_username := p_username, p_local_name := 'Healthcare', p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_housing_type := 'HEALTHCARE');

    -- update in A-1
    PERFORM update_cell(p_cell_path := 'A-1-001', p_prison_id := p_prison_id, p_username := p_username, p_cell_type := array ['ACCESSIBLE_CELL'], p_max_cap := 2, p_working_cap := 2);
    PERFORM update_cell(p_cell_path := 'A-1-002', p_prison_id := p_prison_id, p_username := p_username, p_cell_type := array ['ACCESSIBLE_CELL'], p_max_cap := 2, p_working_cap := 2);
    PERFORM create_inactive_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'PEST', p_deactivation_desc := 'Bed bugs');
    PERFORM create_non_res_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'A-1', p_username := p_username, p_non_res_cell_type := 'OFFICE');
    PERFORM update_cell(p_cell_path := 'A-1-010', p_prison_id := p_prison_id, p_username := p_username, p_cell_type := array ['LISTENER_CRISIS'], p_max_cap := 2, p_working_cap := 1);

    -- cells in A-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_inactive_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'DAMAGED', p_deactivation_desc := 'Flooded');
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['CONSTANT_SUPERVISION']);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_inactive_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'A-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'MAINTENANCE', p_deactivation_desc := 'No running water');


    -- cells in A-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'A-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);

    -- cells in B-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_non_res_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_non_res_cell_type := 'STORE');
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);


    -- cells in B-2
    PERFORM create_inactive_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'DAMP');
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'B-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);


    -- cells in B-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['FIRST_NIGHT_CENTRE']);
    PERFORM create_archive_location(p_code := '013', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');
    PERFORM create_archive_location(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');
    PERFORM create_archive_location(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'B-3', p_username := p_username, p_archive_reason := 'Duplicate');


    -- cells in C-1
    PERFORM create_inactive_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'DAMP', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_inactive_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_deactivation_reason := 'DAMP');
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'C-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1);

    -- cells in C-2
    PERFORM create_non_res_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_non_res_cell_type := 'SHOWER');
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-2', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['LISTENER_CRISIS']);

    -- cells in C-3
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_cell_type := array['ESCAPE_LIST']);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_cell_type := array['LISTENER_CRISIS']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 2, p_working_cap := 1);
    PERFORM create_archive_location(p_code := '014', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_archive_reason := 'Merged to C-3-15');
    PERFORM create_cell(p_code := '015', p_prison_id := p_prison_id, p_parent_path := 'C-3', p_username := p_username, p_max_cap := 4, p_working_cap := 4);

    -- cells in D-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS'], p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 2, p_used_for := array['VULNERABLE_PRISONERS'], p_cell_type := array['LISTENER_CRISIS', 'ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '011', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '012', p_prison_id := p_prison_id, p_parent_path := 'D-1', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);

    -- cells in D-2
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array['VULNERABLE_PRISONERS'], p_cell_type := array['CONSTANT_SUPERVISION']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array['VULNERABLE_PRISONERS'], p_cell_type := array['CONSTANT_SUPERVISION']);
    PERFORM create_cell(p_code := '005', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '006', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '007', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '009', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS']);
    PERFORM create_cell(p_code := '010', p_prison_id := p_prison_id, p_parent_path := 'D-2', p_username := p_username, p_max_cap := 2, p_working_cap := 1, p_used_for := array['VULNERABLE_PRISONERS'], p_cell_type := array['LISTENER_CRISIS']);

    -- D-3 archived cells
    PERFORM create_archive_location(p_code := '3', p_prison_id := p_prison_id, p_parent_path := 'D', p_username := p_username, p_archive_reason := 'Closure', p_location_type := 'LANDING');
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'D-3', p_username := p_username, p_max_cap := 1, p_working_cap := 0);

    -- cells in S-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['CONSTANT_SUPERVISION']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['CONSTANT_SUPERVISION']);
    PERFORM create_cell(p_code := '017', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['DRY']);
    PERFORM create_cell(p_code := '018', p_prison_id := p_prison_id, p_parent_path := 'S-1', p_username := p_username, p_max_cap := 1, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'CARE_AND_SEPARATION', p_cell_type := array['DRY']);


    -- cells in H-1
    PERFORM create_cell(p_code := '001', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '002', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '003', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '004', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_cell_type := array['ACCESSIBLE_CELL']);
    PERFORM create_cell(p_code := '008', p_prison_id := p_prison_id, p_parent_path := 'H-1', p_username := p_username, p_max_cap := 2, p_working_cap := 0, p_used_for := array[]::varchar[], p_accommodation_type := 'HEALTHCARE_INPATIENTS', p_cell_type := array['MEDICAL']);

    -- set up the prison configuration
    insert into prison_configuration (prison_id, resi_location_service_active, certification_approval_required, when_updated, updated_by)
    values (p_prison_id, true, false,now(), p_username);

    insert into signed_operation_capacity (signed_operation_capacity, prison_id, when_updated, updated_by)
    select SUM(COALESCE(c.max_capacity, 0)), l.prison_id,now(), p_username from location l left join capacity c on c.id = l.capacity_id and l.status = 'ACTIVE' where l.prison_id = p_prison_id group by l.prison_id;
END;
$$ LANGUAGE plpgsql;
