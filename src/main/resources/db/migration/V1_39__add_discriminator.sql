ALTER TABLE location ADD COLUMN location_type_discriminator VARCHAR(20);

update location
set location_type_discriminator = (
    case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' when location_type = 'CELL' then 'CELL' when code IN ('RECP', 'COURT', 'TAP', 'CSWAP') then 'VIRTUAL' else 'RESIDENTIAL' end
    );

ALTER TABLE location ALTER COLUMN location_type_discriminator SET NOT NULL;

CREATE INDEX location_type_discriminator_idx ON location (location_type_discriminator, prison_id, path_hierarchy);
