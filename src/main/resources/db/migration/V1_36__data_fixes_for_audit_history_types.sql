DELETE
FROM location_history
where attribute_name = 'OTHER_CONVERTED_CELL_TYPE';

update location_history
set old_value = CASE old_value
                    WHEN 'D' THEN
                        'Refurbishment'
                    WHEN 'G' THEN
                        'Maintenance'
                    WHEN 'H' THEN
                        'Staff shortage'
                    WHEN 'I' THEN
                        'Mothballed'
                    WHEN 'J' THEN
                        'Damage'
                    ELSE
                        'Other'
    END
where attribute_name = 'DEACTIVATED_REASON'
  and old_value IN ('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L');

update location_history
set new_value = CASE new_value
                    WHEN 'D' THEN
                        'Refurbishment'
                    WHEN 'G' THEN
                        'Maintenance'
                    WHEN 'H' THEN
                        'Staff shortage'
                    WHEN 'I' THEN
                        'Mothballed'
                    WHEN 'J' THEN
                        'Damage'
                    ELSE
                        'Other'
    END
where attribute_name = 'DEACTIVATED_REASON'
  and new_value IN ('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L');

update location_history
set old_value = CASE new_value
                    when 'Y' then 'Certified'
                    when 'N' then 'Uncertified'
                    when 'true' then 'Certified'
                    when 'false' then 'Uncertified' END
where attribute_name = 'CERTIFIED';

update location_history
set new_value = CASE new_value
                    when 'Y' then 'Certified'
                    when 'N' then 'Uncertified'
                    when 'true' then 'Certified'
                    when 'false' then 'Uncertified' END
where attribute_name = 'CERTIFIED';

update location_history
set new_value = null
where new_value = '';

update location_history
set old_value = null
where old_value = '';

delete
from location_history
where old_value is null
  and new_value is null;

update location_history l1
set new_value = concat(new_value, ' - ', (SELECT new_value
                                          FROM location_history l2
                                          where attribute_name = 'DEACTIVATED_REASON_DESCRIPTION'
                                            and l2.location_id = l1.location_id
                                            and l2.amended_date = l1.amended_date))
where attribute_name = 'DEACTIVATED_REASON'
  and old_value is null
  and new_value is not null
  and (SELECT new_value
       FROM location_history l2
       where attribute_name = 'DEACTIVATED_REASON_DESCRIPTION'
         and l2.location_id = l1.location_id
         and l2.location_id = l1.location_id
         and l2.amended_date = l1.amended_date) is not null;