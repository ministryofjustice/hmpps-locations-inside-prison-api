-- MAPA-476: remove the capacity left on rooms that were converted from cells.
--
-- Before MAP-3056, converting a cell to a non-residential room removed its capacity row but left its CNA on
-- the certification table. V1_74 then moved CNA onto capacity and created a capacity row of max 0, working 0
-- and the old CNA for every location that had none, converted rooms included. Those rooms have been counted
-- in wing and prison CNA totals ever since. A conversion today removes the capacity row altogether, so this
-- puts the affected rooms back to the same state.
--
-- Only rooms matching the V1_74 pattern (max 0, working 0, CNA above 0) are touched. Converted rooms holding
-- real max or working capacity are left alone. The Five Wells reception locations are handled separately by V1_116.
--
-- Each change is recorded in location_history under one linked transaction per prison, so it shows in the
-- location's history. Times are London local time, matching what the application writes. A migration
-- publishes no domain events, so NOMIS is not updated by this; the rooms and the locations above them are
-- re-synced afterwards with the to-NOMIS repair (see MAPA-476).

CREATE TEMP TABLE _converted_rooms_with_capacity AS
SELECT l.id                             AS location_id,
       l.prison_id,
       l.capacity_id,
       c.certified_normal_accommodation AS cna
FROM location l
         JOIN capacity c ON c.id = l.capacity_id
WHERE l.converted_cell_type IS NOT NULL
  AND c.max_capacity = 0
  AND c.working_capacity = 0
  AND c.certified_normal_accommodation > 0;

CREATE TEMP TABLE _mapa_476_transaction AS
SELECT gen_random_uuid() AS id, prison_id
FROM (SELECT DISTINCT prison_id FROM _converted_rooms_with_capacity) prisons;

INSERT INTO linked_transaction (id, prison_id, transaction_type, transaction_detail, transaction_invoked_by, tx_start_time, tx_end_time)
SELECT t.id,
       t.prison_id,
       'CAPACITY_CHANGE',
       'MAPA-476: removed certified normal accommodation left on rooms converted from cells',
       'LOCATIONS_INSIDE_PRISON_API',
       now() AT TIME ZONE 'Europe/London',
       now() AT TIME ZONE 'Europe/London'
FROM _mapa_476_transaction t;

INSERT INTO location_history (location_id, attribute_name, old_value, new_value, amended_by, amended_date, linked_transaction_id)
SELECT r.location_id,
       'CERTIFIED_CAPACITY',
       r.cna::text,
       'None',
       'LOCATIONS_INSIDE_PRISON_API',
       now() AT TIME ZONE 'Europe/London',
       t.id
FROM _converted_rooms_with_capacity r
         JOIN _mapa_476_transaction t ON t.prison_id = r.prison_id;

UPDATE location l
SET capacity_id  = NULL,
    updated_by   = 'LOCATIONS_INSIDE_PRISON_API',
    when_updated = now() AT TIME ZONE 'Europe/London'
FROM _converted_rooms_with_capacity r
WHERE l.id = r.location_id;

DELETE
FROM capacity c
    USING _converted_rooms_with_capacity r
WHERE c.id = r.capacity_id;

DROP TABLE _mapa_476_transaction;
DROP TABLE _converted_rooms_with_capacity;
