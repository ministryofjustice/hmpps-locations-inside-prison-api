update location l
SET accommodation_type =
        (select CASE
                    WHEN  (landing.residential_housing_type = 'NORMAL_ACCOMMODATION' or spur.residential_housing_type = 'NORMAL_ACCOMMODATION' or wing.residential_housing_type = 'NORMAL_ACCOMMODATION') THEN 'NORMAL_ACCOMMODATION'
                    WHEN  (landing.residential_housing_type = 'HEALTHCARE' or spur.residential_housing_type = 'HEALTHCARE' or wing.residential_housing_type = 'HEALTHCARE')  THEN 'HEALTHCARE_INPATIENTS'
                    WHEN  (landing.residential_housing_type = 'SEGREGATION' or spur.residential_housing_type = 'SEGREGATION' or wing.residential_housing_type = 'SEGREGATION')  THEN 'CARE_AND_SEPARATION'
                    ELSE 'OTHER_NON_RESIDENTIAL'
                    END
         from location l2
                  left join location landing on landing.id = l2.parent_id
                  left join location spur on spur.id = landing.parent_id
                  left join location wing on wing.id = spur.parent_id
         WHERE l2.id = l.id
        )
WHERE (l.accommodation_type = 'OTHER_NON_RESIDENTIAL') and l.residential_housing_type is not null
  and l.active = true;