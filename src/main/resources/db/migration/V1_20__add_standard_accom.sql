INSERT INTO cell_used_for (location_id, used_for)
SELECT id, 'STANDARD_ACCOMMODATION'
from location
where residential_housing_type IS NOT NULL and location_type = 'CELL';