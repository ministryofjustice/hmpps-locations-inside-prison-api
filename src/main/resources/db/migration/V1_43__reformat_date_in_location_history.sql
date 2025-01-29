UPDATE location_history
set new_value = TO_CHAR(TO_DATE(new_value, 'DD-MON-YY'), 'DD/MM/YYYY')
WHERE new_value IS NOT NULL and attribute_name = 'PROPOSED_REACTIVATION_DATE'
  AND new_value LIKE '__-___-__';

UPDATE location_history
set old_value = TO_CHAR(TO_DATE(old_value, 'DD-MON-YY'), 'DD/MM/YYYY')
WHERE old_value IS NOT NULL and attribute_name = 'PROPOSED_REACTIVATION_DATE'
  AND old_value LIKE '__-___-__';

UPDATE location_history
set new_value = TO_CHAR(TO_DATE(new_value, 'YYYY-MM-DD'), 'DD/MM/YYYY')
WHERE new_value IS NOT NULL and attribute_name = 'PROPOSED_REACTIVATION_DATE'
  AND new_value LIKE '____-__-__';

UPDATE location_history
set old_value = TO_CHAR(TO_DATE(old_value, 'YYYY-MM-DD'), 'DD/MM/YYYY')
WHERE old_value IS NOT NULL and attribute_name = 'PROPOSED_REACTIVATION_DATE'
  AND old_value LIKE '____-__-__';
