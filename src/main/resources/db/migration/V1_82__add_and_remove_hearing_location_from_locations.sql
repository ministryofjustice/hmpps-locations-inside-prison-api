-- This inserts one row per location where location_type = 'ADJUDICATION_ROOM'
-- and there is currently no service_usage row with service_type = 'HEARING_LOCATION'.
INSERT INTO service_usage (location_id, service_type)
SELECT l.id, 'HEARING_LOCATION'
FROM location l
WHERE location_type_discriminator = 'NON_RESIDENTIAL'
  AND l.location_type = 'ADJUDICATION_ROOM'
  AND NOT EXISTS (
    SELECT 1
    FROM service_usage su
    WHERE su.location_id = l.id
      AND su.service_type = 'HEARING_LOCATION'
);

-- This removes service_usage rows where service_type = 'HEARING_LOCATION' but the associated location.location_type is anything other than ADJUDICATION_ROOM
DELETE FROM service_usage su
    USING location l
WHERE su.location_id = l.id
  and location_type_discriminator = 'NON_RESIDENTIAL'
  AND su.service_type = 'HEARING_LOCATION'
  AND l.location_type <> 'ADJUDICATION_ROOM';
