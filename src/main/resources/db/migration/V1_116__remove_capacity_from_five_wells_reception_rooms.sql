-- MAPA-476: remove the capacity held on the five reception sub-locations at Five Wells (FWI).
--
-- RECP-1-1 to RECP-1-5 are virtual reception locations under RECP-1, each holding max 15, working 15 and CNA 15.
-- They are not on the cell certificate and capacity management confirmed they should not count towards the
-- prison's figures. The other reception sub-locations at Five Wells (RECP-2 to RECP-5) hold no capacity, so this
-- puts these five into the same state by removing their capacity row.
--
-- Reception moves are not affected: the space check in reception reads only the capacity of RECP itself.
--
-- Only these five locations are touched, and only while they are still virtual and still hold capacity. Each change
-- is recorded in location_history under one linked transaction. A migration publishes no domain events, so NOMIS is
-- not updated by this; the locations are re-synced afterwards with the to-NOMIS repair.

CREATE TEMP TABLE _fwi_reception_rooms AS
SELECT l.id                             AS location_id,
       l.prison_id,
       l.capacity_id,
       c.max_capacity,
       c.working_capacity,
       c.certified_normal_accommodation AS cna
FROM location l
         JOIN capacity c ON c.id = l.capacity_id
WHERE l.prison_id = 'FWI'
  AND l.path_hierarchy IN ('RECP-1-1', 'RECP-1-2', 'RECP-1-3', 'RECP-1-4', 'RECP-1-5')
  AND l.location_type_discriminator = 'VIRTUAL';

CREATE TEMP TABLE _mapa_476_fwi_transaction AS
SELECT gen_random_uuid() AS id, prison_id
FROM (SELECT DISTINCT prison_id FROM _fwi_reception_rooms) prisons;

INSERT INTO linked_transaction (id, prison_id, transaction_type, transaction_detail, transaction_invoked_by, tx_start_time, tx_end_time)
SELECT t.id,
       t.prison_id,
       'CAPACITY_CHANGE',
       'MAPA-476: removed capacity from reception locations that are not on the cell certificate',
       'LOCATIONS_INSIDE_PRISON_API',
       now() AT TIME ZONE 'Europe/London',
       now() AT TIME ZONE 'Europe/London'
FROM _mapa_476_fwi_transaction t;

INSERT INTO location_history (location_id, attribute_name, old_value, new_value, amended_by, amended_date, linked_transaction_id)
SELECT r.location_id,
       a.attribute_name,
       a.old_value,
       'None',
       'LOCATIONS_INSIDE_PRISON_API',
       now() AT TIME ZONE 'Europe/London',
       t.id
FROM _fwi_reception_rooms r
         JOIN _mapa_476_fwi_transaction t ON t.prison_id = r.prison_id
         CROSS JOIN LATERAL (VALUES ('MAX_CAPACITY', r.max_capacity::text),
                                    ('WORKING_CAPACITY', r.working_capacity::text),
                                    ('CERTIFIED_CAPACITY', r.cna::text)) AS a(attribute_name, old_value);

UPDATE location l
SET capacity_id  = NULL,
    updated_by   = 'LOCATIONS_INSIDE_PRISON_API',
    when_updated = now() AT TIME ZONE 'Europe/London'
FROM _fwi_reception_rooms r
WHERE l.id = r.location_id;

DELETE
FROM capacity c
    USING _fwi_reception_rooms r
WHERE c.id = r.capacity_id;

DROP TABLE _mapa_476_fwi_transaction;
DROP TABLE _fwi_reception_rooms;
