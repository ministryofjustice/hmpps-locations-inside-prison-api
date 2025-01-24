UPDATE location_history
SET attribute_name = CASE attribute_name
                         WHEN 'CAPACITY' THEN
                             'MAX_CAPACITY'
                         WHEN 'OPERATIONAL_CAPACITY' THEN
                             'WORKING_CAPACITY'
                         WHEN 'DESCRIPTION' THEN
                             'LOCAL_NAME'
                         WHEN 'CERTIFIED' THEN
                             'CERTIFICATION'
                         ELSE attribute_name
    END
WHERE attribute_name IN ('CAPACITY', 'OPERATIONAL_CAPACITY', 'DESCRIPTION', 'CERTIFIED');