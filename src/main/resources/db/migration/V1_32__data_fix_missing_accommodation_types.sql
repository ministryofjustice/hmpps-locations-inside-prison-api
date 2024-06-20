UPDATE location
SET accommodation_type =
        CASE
            WHEN residential_housing_type = 'NORMAL_ACCOMMODATION'  THEN 'NORMAL_ACCOMMODATION'
            WHEN residential_housing_type = 'HEALTHCARE'  THEN 'HEALTHCARE_INPATIENTS'
            WHEN residential_housing_type = 'SEGREGATION'  THEN 'CARE_AND_SEPARATION'
            ELSE 'OTHER_NON_RESIDENTIAL'
            END
WHERE accommodation_type is null
  and (case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' when location_type = 'CELL' then 'CELL' else 'RESIDENTIAL' end) = 'CELL';

INSERT INTO cell_used_for (location_id, used_for)
SELECT id as location_id, 'STANDARD_ACCOMMODATION' as used_for
from location
where accommodation_type = 'NORMAL_ACCOMMODATION'
  and (case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' when location_type = 'CELL' then 'CELL' else 'RESIDENTIAL' end) = 'CELL'
  and not exists (select 1 from cell_used_for cuf where cuf.location_id = location.id and used_for = 'STANDARD_ACCOMMODATION')