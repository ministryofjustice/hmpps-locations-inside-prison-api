UPDATE specialist_cell
    set specialist_cell_type = 'ACCESSIBLE_CELL' WHERE specialist_cell_type = 'WHEELCHAIR_ACCESS';

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'BIOHAZARD_DIRTY_PROTEST' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'BIOHAZARD_DIRTY_PROTEST')
and comments IN ( );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'CONSTANT_SUPERVISION' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'CONSTANT_SUPERVISION')
  and comments IN ( );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'CSU' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'CSU')
  and comments IN ( );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'DDA_COMPLIANT' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'DDA_COMPLIANT')
  and comments IN ( );


INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'DRY' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'DRY')
  and comments IN (
'Dry Cell / High Control',
'SINGLE (DRY)'
    );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'ESCAPE_LIST' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'ESCAPE_LIST')
  and comments IN (
'E LIST CELL',
'E LIST CELL',
'E-Wing'  );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'ISOLATION_DISEASES' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'ISOLATION_DISEASES')
  and comments IN ('Infectious diseases cell - Not on Op-Cap' );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'LISTENER_CRISIS' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'LISTENER_CRISIS')
  and comments IN (
'Listener cell',
'Listener Suite',
'Listener''s suite',
'Listeners PAL Suite',
'Listeners suite',
'Listerners Crisis Suite',
'CRISIS SUITE',
'Support',
'SUPPORT SUITE'
    );

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'ACCESSIBLE_CELL' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'ACCESSIBLE_CELL')
and comments IN (
'Wheelchair access',
'Adapted for wheelchair/disabled access'
);

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'MOTHER_AND_BABY' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id and specialist_cell_type = 'MOTHER_AND_BABY')
  and comments IN (
                   'prisoner with 1 baby',
                   'prisoner with 1 baby',
                   'prisoner with 1 baby',
                   'prisoner with up to 2 babies',
                   'prisoner with 1 baby',
                   'prisoner with 1 baby',
                   'prisoner with 1 baby',
                   'prisoner with baby',
                   'prisoner with baby');

INSERT INTO specialist_cell (location_id, specialist_cell_type)
SELECT id, 'SAFE_CELL' from location ra
where not exists (select 1 from specialist_cell where specialist_cell.location_id = ra.id
                                                  and specialist_cell_type = 'SAFE_CELL')
and comments IN (
    'Safer Cell/ Special door metal glass combo',
    'IDTS Safer Single',
    'IDTS Safer Double'
);
