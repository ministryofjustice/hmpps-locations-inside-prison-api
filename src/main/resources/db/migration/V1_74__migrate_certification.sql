ALTER TABLE location ADD COLUMN certified_cell boolean NULL;

UPDATE location l
SET certified_cell = cert.certified
FROM certification cert
WHERE l.certification_id = cert.id
  AND l.certified_cell is null;

update location set certified_cell = false where certified_cell is null and location_type_discriminator = 'CELL';

ALTER TABLE capacity ADD COLUMN certified_normal_accommodation integer NOT NULL DEFAULT 0;

UPDATE capacity c
SET certified_normal_accommodation = cert.certified_normal_accommodation
FROM certification cert
         JOIN location l ON l.certification_id = cert.id
WHERE l.capacity_id = c.id
  AND cert.certified_normal_accommodation > 0
  AND c.certified_normal_accommodation = 0;

-- Collect the locations that need capacity rows
CREATE TEMP TABLE _missing_locs AS
SELECT ROW_NUMBER() OVER (ORDER BY l.id) AS rn,
       l.id AS location_id,
       cert.certified_normal_accommodation AS cna
FROM location l
         JOIN certification cert ON cert.id = l.certification_id
         LEFT JOIN capacity c ON c.id = l.capacity_id
WHERE l.capacity_id IS NULL
  AND cert.certified_normal_accommodation > 0;

-- Insert capacity rows in the same order, capture generated ids alongside the same ordinal
CREATE TEMP TABLE _created_caps AS
WITH ins AS (
    INSERT INTO capacity (max_capacity, working_capacity, certified_normal_accommodation)
        SELECT 0, 0, m.cna
        FROM _missing_locs m
        ORDER BY m.rn
        RETURNING id
)
SELECT ROW_NUMBER() OVER () AS rn, id
FROM ins;

-- Join by rn to update the locations with the new capacity ids
UPDATE location l
SET capacity_id = cc.id
FROM _missing_locs m
         JOIN _created_caps cc USING (rn)
WHERE l.id = m.location_id
  AND l.capacity_id IS NULL;

DROP TABLE _created_caps;
DROP TABLE _missing_locs;

--back up the certification table
CREATE TABLE certification_backup AS SELECT l.id location_id, c.id certificate_id, certified, certified_normal_accommodation FROM certification c join location l on l.certification_id = c.id;

ALTER TABLE location DROP COLUMN certification_id;
DROP TABLE certification CASCADE;